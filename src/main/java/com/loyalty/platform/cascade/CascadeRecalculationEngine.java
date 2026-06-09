package com.loyalty.platform.cascade;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.domain.entity.*;
import com.loyalty.platform.domain.entity.MemberAccount;
import com.loyalty.platform.domain.repository.*;
import com.loyalty.platform.accounting.PointRedeemService;
import com.loyalty.platform.accounting.PointGrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 级联重算引擎 —— 异步无锁化影子回放 + 短事务差额补偿。
 *
 * <p>按照设计文档 5.3 节实现。核心思想：
 * <ol>
 *   <li><b>异步回放</b>：不锁账户，在内存中构建影子上下文，回放历史事件。</li>
 *   <li><b>计算 Delta</b>：对比影子推演结果与当前真实状态，得出差额。</li>
 *   <li><b>短事务补偿</b>：仅在最终写入时开启 DB 强事务，几毫秒完成。</li>
 *   <li><b>防重与恢复</b>：幂等校验 + 卡死任务定期恢复。</li>
 * </ol>
 *
 * <p><b>线程安全</b>：异步执行，每次重算创建独立的 ShadowContext 实例。
 * 短事务补偿通过 cascade_recalc_log 的 reverse_event_id 唯一性实现幂等。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Service
public class CascadeRecalculationEngine {

    private static final Logger log = LoggerFactory.getLogger(CascadeRecalculationEngine.class);
    private static final int SCALE = 4;

    private final CascadeRecalcJobRepository jobRepo;
    private final CascadeRecalcLogRepository logRepo;
    private final TierChangeLogRepository tierLogRepo;
    private final MemberAccountRepository accountRepo;
    private final AccountTransactionRepository txRepo;
    private final PointRedeemService redeemService;
    private final PointGrantService grantService;

    public CascadeRecalculationEngine(CascadeRecalcJobRepository jobRepo,
                                       CascadeRecalcLogRepository logRepo,
                                       TierChangeLogRepository tierLogRepo,
                                       MemberAccountRepository accountRepo,
                                       AccountTransactionRepository txRepo,
                                       PointRedeemService redeemService,
                                       PointGrantService grantService) {
        this.jobRepo = jobRepo;
        this.logRepo = logRepo;
        this.tierLogRepo = tierLogRepo;
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.redeemService = redeemService;
        this.grantService = grantService;
    }

    // ==================== 核心：级联重算 ====================

    /**
     * 异步执行级联重算。
     *
     * <p>流程：
     * <ol>
     *   <li>加载退款后时间轴上的所有正向事件流水（不加锁）</li>
     *   <li>构建影子上下文（含等级变更时间线）</li>
     *   <li>按时间轴逐事件回放：推进等级 → 用历史规则重算 → 记录影子交易</li>
     *   <li>计算 Delta 差额</li>
     *   <li>短事务提交补偿</li>
     * </ol>
     *
     * @param jobId      重算任务 ID
     * @param programCode 租户代码
     * @param memberId   会员 ID
     * @param reverseTime 退款发生时间（回放起点）
     */
    @Async
    public void processCascadeRecalculation(String jobId, String programCode,
                                             Long memberId, LocalDateTime reverseTime) {
        log.info("[Cascade] 开始级联重算: jobId={}, member={}, reverseTime={}", jobId, memberId, reverseTime);
        TenantContext.set(programCode);

        try {
            // 1. 标记任务为 RUNNING
            var jobOpt = jobRepo.findByJobId(programCode, jobId);
            if (jobOpt.isEmpty()) {
                log.error("[Cascade] 任务不存在: jobId={}", jobId);
                return;
            }
            CascadeRecalcJob job = jobOpt.get();
            job.setStatus("RUNNING");
            job.setStartedAt(LocalDateTime.now());
            jobRepo.save(job);

            // 2. 构建影子上下文（加载等级变更时间线）
            ShadowContext shadow = buildShadowContext(programCode, memberId, reverseTime);

            // 3. 加载回放时间轴上的所有正向事件（accrual 类型流水）
            //    这里简化为加载 account_transaction 中 reverseTime 之后的 ACCRUAL/ADJUSTMENT 流水
            //    实际生产环境应从 transaction_event 表加载完整事件链
            List<AccountTransaction> timelineTxs = loadTimelineTransactions(programCode, memberId, reverseTime);

            int recalcOrder = 0;
            for (AccountTransaction tx : timelineTxs) {
                shadow.advanceToTime(tx.getCreatedAt());

                // 计算该事件在当前等级下应得的积分
                // 实际生产环境：调用 ruleEngine.evaluate(shadow, event, snapshot)
                BigDecimal recalculated = recalculatePoints(programCode, tx, shadow.getCurrentTier());
                BigDecimal original = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                BigDecimal diff = recalculated.subtract(original);

                if (diff.abs().compareTo(new BigDecimal("0.0001")) > 0) {
                    shadow.apply(tx.getReferenceEventId(),
                            tx.getTransactionType(), recalculated, null);
                }

                // 记录重算明细
                saveRecalcLog(programCode, memberId, reverseTime.toString(),
                        tx.getReferenceEventId(), original, recalculated, diff,
                        shadow.getCurrentTier(), ++recalcOrder);
            }

            // 4. 计算与真实状态的差额
            AccountDelta delta = calculateDelta(programCode, memberId, shadow);

            // 5. 短事务提交补偿
            if (!delta.isEmpty()) {
                applyCompensationWithShortTransaction(programCode, memberId, delta, jobId);
            } else {
                log.info("[Cascade] 无差额，无需补偿: jobId={}", jobId);
            }

            // 6. 标记完成
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(LocalDateTime.now());
            job.setAffectedCount(recalcOrder);
            jobRepo.save(job);

            log.info("[Cascade] 级联重算完成: jobId={}, affectedEvents={}", jobId, recalcOrder);

        } catch (Exception e) {
            log.error("[Cascade] 级联重算失败: jobId={}", jobId, e);
            var jobOpt = jobRepo.findByJobId(programCode, jobId);
            jobOpt.ifPresent(j -> {
                j.setStatus("FAILED");
                j.setErrorMessage(e.getMessage());
                j.setFinishedAt(LocalDateTime.now());
                jobRepo.save(j);
            });
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 极短事务提交补偿差额 —— 级联重算的最后一步，耗时几毫秒。
     *
     * <p><b>幂等保证</b>：同一 jobId 的补偿只能执行一次（通过 cascade_recalc_log 防重）。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void applyCompensationWithShortTransaction(String programCode, Long memberId,
                                                          AccountDelta delta, String jobId) {
        // 幂等校验
        if (logRepo.existsByReverseEventId(programCode, jobId)) {
            log.info("[Cascade] 补偿已执行，跳过: jobId={}", jobId);
            return;
        }

        // 差额扣减（多发的积分追回）
        if (delta.getPointsToDeduct().compareTo(BigDecimal.ZERO) > 0) {
            try {
                redeemService.redeemPoints(programCode, memberId, "REWARD_POINTS", delta.getPointsToDeduct());
            } catch (Exception e) {
                log.error("[Cascade] 差额扣减失败，尝试透支追回: member={}, amount={}", memberId, delta.getPointsToDeduct());
                // forceDeduct 通过透支模式追回
                forceDeductWithOverdraft(programCode, memberId, delta.getPointsToDeduct());
            }
        }

        // 差额补发（少发的积分补偿）
        if (delta.getPointsToAdd().compareTo(BigDecimal.ZERO) > 0) {
            grantService.grantPoints(programCode, memberId, "REWARD_POINTS",
                    delta.getPointsToAdd(), "CASCADE_RECALC_COMPENSATE", null);
        }

        // 修正等级（在 member_tier 表中更新）
        if (delta.hasTierChange()) {
            // 写入 tier_change_log
            TierChangeLog tcl = TierChangeLog.builder()
                    .programCode(programCode)
                    .memberId(memberId)
                    .fromTier(delta.getOldTier())
                    .toTier(delta.getNewTier())
                    .changeReason("CASCADE_RECALC")
                    .eventId(jobId)
                    .changedAt(LocalDateTime.now())
                    .build();
            tierLogRepo.save(tcl);

            log.info("[Cascade] 等级修正: member={}, {}→{}", memberId, delta.getOldTier(), delta.getNewTier());
        }

        // 标记补偿完成（防重）
        logRepo.save(CascadeRecalcLog.builder()
                .programCode(programCode)
                .reverseEventId(jobId)
                .memberId(memberId)
                .recalcOrder(0)
                .build());

        jobRepo.markCompleted(
                jobRepo.findByJobId(programCode, jobId).map(CascadeRecalcJob::getId).orElse(null));

        log.info("[Cascade] 短事务补偿完成: jobId={}, deduct={}, add={}, tierChange={}",
                jobId, delta.getPointsToDeduct(), delta.getPointsToAdd(), delta.hasTierChange());
    }

    // ==================== 故障恢复 ====================

    /**
     * 定时扫描卡在 RUNNING 状态超过 5 分钟的任务，重置为 PENDING。
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void recoverStuckJobs() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(5);
        List<CascadeRecalcJob> stuckJobs = jobRepo.findStuckJobs(null, timeout);
        for (CascadeRecalcJob job : stuckJobs) {
            log.warn("[Cascade] 恢复卡死任务: jobId={}, startedAt={}", job.getJobId(), job.getStartedAt());
            job.setStatus("PENDING");
            job.setStartedAt(null);
            jobRepo.save(job);
        }
        if (!stuckJobs.isEmpty()) {
            log.info("[Cascade] 恢复了 {} 个卡死任务", stuckJobs.size());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 逐会员扫描 PENDING 任务并提交异步执行。
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingJobs() {
        List<CascadeRecalcJob> pending = jobRepo.findPendingJobs(null);
        for (CascadeRecalcJob job : pending) {
            if (job.getCursorEventTime() == null) {
                job.setCursorEventTime(job.getCreatedAt());
            }
            processCascadeRecalculation(job.getJobId(), job.getProgramCode(),
                    job.getMemberId(), job.getCursorEventTime());
        }
        if (!pending.isEmpty()) {
            log.info("[Cascade] 提交 {} 个重算任务", pending.size());
        }
    }

    private ShadowContext buildShadowContext(String programCode, Long memberId, LocalDateTime reverseTime) {
        List<TierChangeLog> tierLogs = tierLogRepo.findByMemberOrderByTime(programCode, memberId);

        List<ShadowContext.TierChangeRecord> timeline = tierLogs.stream()
                .filter(t -> t.getChangedAt() != null && t.getChangedAt().isAfter(reverseTime))
                .map(t -> new ShadowContext.TierChangeRecord(
                        t.getFromTier(), t.getToTier(), t.getChangeReason(), t.getChangedAt()))
                .toList();

        // 找到 reverseTime 之前的最后一个等级作为初始等级
        String initialTier = tierLogs.stream()
                .filter(t -> t.getChangedAt() != null && !t.getChangedAt().isAfter(reverseTime))
                .max(Comparator.comparing(TierChangeLog::getChangedAt))
                .map(TierChangeLog::getToTier)
                .orElse("BASE");

        return new ShadowContext(programCode, String.valueOf(memberId), timeline, initialTier);
    }

    private List<AccountTransaction> loadTimelineTransactions(String programCode, Long memberId,
                                                               LocalDateTime after) {
        // 从 account_transaction 加载 reverseTime 之后的 ACCRUAL/REDEMPTION 流水
        // 用于影子回放：按时间升序重算每笔交易在当前等级下应得的积分
        return txRepo.findTimelineAfter(programCode, memberId, after);
    }

    private BigDecimal recalculatePoints(String programCode, AccountTransaction tx, String currentTier) {
        // 简化的重算逻辑：实际应调用 RuleEngine.evaluate(shadow, event, snapshot)
        BigDecimal base = tx.getAmount() != null ? tx.getAmount().abs() : BigDecimal.ZERO;
        // 示例：金卡双倍
        if ("GOLD".equals(currentTier)) {
            return base.multiply(new BigDecimal("2")).setScale(SCALE, RoundingMode.HALF_UP);
        }
        return base.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private AccountDelta calculateDelta(String programCode, Long memberId, ShadowContext shadow) {
        // 获取当前真实账户余额（ACTIVE 批次 remaining_amount 之和）
        BigDecimal realBalance = txRepo.sumAvailableBalance(programCode, memberId, "REWARD_POINTS");

        BigDecimal shadowBalance = shadow.getShadowBalance();
        BigDecimal diff = shadowBalance.subtract(realBalance);

        // diff > 0: 影子推演余额高于真实余额 → 需要补发积分
        // diff < 0: 影子推演余额低于真实余额 → 需要追回积分
        BigDecimal pointsToDeduct = BigDecimal.ZERO;
        BigDecimal pointsToAdd = BigDecimal.ZERO;
        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            pointsToDeduct = diff.abs().setScale(SCALE, RoundingMode.HALF_UP);
        } else if (diff.compareTo(BigDecimal.ZERO) > 0) {
            pointsToAdd = diff.setScale(SCALE, RoundingMode.HALF_UP);
        }

        // 等级变更检查：对比当前真实等级与影子推演结束时的等级
        String shadowEndTier = shadow.getCurrentTier();
        String realTier = accountRepo.findByMemberIdAndType(programCode, memberId, "REWARD_POINTS")
                .map(MemberAccount::getAccountType)
                .orElse(null);
        // 使用影子推演的最终等级作为新等级（仅当与旧等级不同时触发修改）
        String newTier = null;
        String oldTier = null;
        if (shadowEndTier != null && !shadowEndTier.equals(realTier)) {
            // 影子推演得出不同等级 → 需要修正
            oldTier = realTier;
            newTier = shadowEndTier;
        }

        AccountDelta delta = new AccountDelta(pointsToDeduct, pointsToAdd, newTier, oldTier,
                shadow.getShadowTransactions().size());

        log.debug("[Cascade] Delta 计算: shadowBalance={}, realBalance={}, deduct={}, add={}, tierChange={}→{}",
                shadowBalance, realBalance, pointsToDeduct, pointsToAdd, oldTier, newTier);
        return delta;
    }

    private void saveRecalcLog(String programCode, Long memberId, String reverseEventId,
                               String affectedEventId, BigDecimal original, BigDecimal recalculated,
                               BigDecimal diff, String tier, int order) {
        logRepo.save(CascadeRecalcLog.builder()
                .programCode(programCode).reverseEventId(reverseEventId)
                .memberId(memberId).affectedEventId(affectedEventId)
                .originalPoints(original).recalculatedPoints(recalculated)
                .pointsDiff(diff).recalculatedTier(tier).recalcOrder(order).build());
    }

    private void forceDeductWithOverdraft(String programCode, Long memberId, BigDecimal amount) {
        // 生成 CASCADE_DEDUCT 透支流水
        AccountTransaction deduct = AccountTransaction.builder()
                .programCode(programCode).memberId(memberId)
                .accountType("REWARD_POINTS")
                .transactionType("CASCADE_DEDUCT")
                .amount(amount.negate().setScale(SCALE, RoundingMode.HALF_UP))
                .remainingAmount(amount.negate().setScale(SCALE, RoundingMode.HALF_UP))
                .status("ACTIVE")
                .operationKey(programCode + ":CASCADE_DEDUCT:" + memberId + ":" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now())
                .build();
        txRepo.save(deduct);
    }
}