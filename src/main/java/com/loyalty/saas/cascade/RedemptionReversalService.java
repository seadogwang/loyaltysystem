package com.loyalty.saas.cascade;

import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.common.event.EventBridge;
import com.loyalty.saas.common.exception.BusinessException;
import com.loyalty.saas.accounting.PointsExpiredEvent;
import com.loyalty.saas.domain.entity.AccountTransaction;
import com.loyalty.saas.domain.entity.RedemptionAllocation;
import com.loyalty.saas.domain.repository.AccountTransactionRepository;
import com.loyalty.saas.domain.repository.RedemptionAllocationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退换货积分还原服务 —— REDEMPTION_CANCEL 生命周期精准还原。
 *
 * <p>设计文档 5.4 节实现。核心逻辑：
 * <ol>
 *   <li>追溯分摊记录：查 redemption_allocation 找到原核销扣减了哪些 ACCRUAL 批次</li>
 *   <li>批次还原：将分摊额度加回原始批次的 remaining_amount</li>
 *   <li>过期重裁决：
 *     <ul>
 *       <li>expires_at > 当前时间 → 正常恢复</li>
 *       <li>expires_at <= 当前时间 → 拦截作废（除非在宽限期内）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>安全机制</b>：防止"兑换再退款"实现积分强制续期的漏洞。
 * 恢复的积分保持原始批次的生命周期（expires_at 不变），而不是新发一笔积分。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Service
public class RedemptionReversalService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionReversalService.class);

    private final RedemptionAllocationRepository allocationRepo;
    private final AccountTransactionRepository txRepo;
    private final EventBridge eventBridge;
    @PersistenceContext
    private EntityManager em;

    /** 退款过期积分宽限期（天），从 Program config_json 读取 */
    private static final int DEFAULT_GRACE_DAYS = 7;

    private static final int SCALE = 4;

    public RedemptionReversalService(RedemptionAllocationRepository allocationRepo,
                                      AccountTransactionRepository txRepo,
                                      @Autowired(required = false) EventBridge eventBridge) {
        this.allocationRepo = allocationRepo;
        this.txRepo = txRepo;
        this.eventBridge = eventBridge;
    }

    /**
     * 处理 REDEMPTION_CANCEL 事件——取消兑换，还原积分。
     *
     * <p>如果原始批次已过期，根据宽限期配置处理：
     * <ul>
     *   <li>在宽限期内 → 正常恢复，用户体验优先</li>
     *   <li>超过宽限期 → 直接作废，生成 EXPIRATION 事件</li>
     * </ul>
     *
     * @param programCode           租户代码
     * @param redemptionTransactionId 原核销流水 ID
     * @param graceDays             宽限天数（0 表示不启用宽限期）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelRedemption(String programCode, Long redemptionTransactionId, int graceDays) {
        if (programCode == null || programCode.isBlank()) {
            throw new BusinessException("ERR_INVALID_PARAM", "programCode is required");
        }
        if (redemptionTransactionId == null) {
            throw new BusinessException("ERR_INVALID_PARAM", "redemptionTransactionId is required");
        }

        log.info("[Reversal] REDEMPTION_CANCEL 开始: program={}, redemptionTxId={}, graceDays={}",
                programCode, redemptionTransactionId, graceDays);

        // ---- Step 1: 追溯分摊记录 ----
        List<RedemptionAllocation> allocations = allocationRepo.findByRedemptionTxId(
                programCode, redemptionTransactionId);

        if (allocations.isEmpty()) {
            log.warn("[Reversal] 无分摊记录: redemptionTxId={}, 可能已被取消", redemptionTransactionId);
            return;
        }

        log.info("[Reversal] 找到 {} 条分摊记录", allocations.size());

        int restoredCount = 0;
        int expiredCount = 0;
        int graceCount = 0;
        BigDecimal totalRestored = BigDecimal.ZERO;
        BigDecimal totalExpired = BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        int effectiveGraceDays = graceDays > 0 ? graceDays : DEFAULT_GRACE_DAYS;
        LocalDateTime graceCutoff = now.minusDays(effectiveGraceDays);

        // ---- Step 2: 逐笔还原原始批次 ----
        for (RedemptionAllocation allocation : allocations) {
            Long accrualTxId = allocation.getAccrualTransactionId();
            BigDecimal restoreAmount = allocation.getAllocatedAmount();

            if (restoreAmount == null || restoreAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            restoreAmount = restoreAmount.setScale(SCALE, RoundingMode.HALF_UP);

            AccountTransaction accrual = em.find(AccountTransaction.class, accrualTxId);
            if (accrual == null) {
                log.warn("[Reversal] 原始 ACCRUAL 批次不存在: txId={}", accrualTxId);
                continue;
            }

            // ---- Step 3: 过期判定重裁决 ----
            LocalDateTime expiresAt = accrual.getExpiresAt();

            if (expiresAt != null && expiresAt.isBefore(now)) {
                // 过期了——检查是否在宽限期内
                if (expiresAt.isAfter(graceCutoff)) {
                    // 在宽限期内，正常恢复
                    graceCount++;
                    log.info("[Reversal] 宽限期内恢复: batchId={}, expiredAt={}, graceDays={}",
                            accrualTxId, expiresAt, effectiveGraceDays);
                } else {
                    // 超过宽限期→拦截作废
                    expiredCount++;
                    totalExpired = totalExpired.add(restoreAmount);

                    // 发布过期事件
                    if (eventBridge != null) {
                        eventBridge.publish("loyalty-point-events",
                                String.valueOf(accrual.getMemberId()),
                                new PointsExpiredEvent(programCode, accrual.getMemberId(),
                                        restoreAmount, accrualTxId, accrual.getAccountType()));
                    }
                    log.info("[Reversal] 过期拦截: batchId={}, expiredAt={}, amount={}",
                            accrualTxId, expiresAt, restoreAmount);
                    continue;
                }
            }

            // ---- Step 4: 加回 remaining_amount ----
            BigDecimal currentRemaining = accrual.getRemainingAmount() != null
                    ? accrual.getRemainingAmount() : BigDecimal.ZERO;
            BigDecimal newRemaining = currentRemaining.add(restoreAmount);
            accrual.setRemainingAmount(newRemaining);

            // 如果批次原为 EXHAUSTED，恢复为 ACTIVE
            if ("EXHAUSTED".equals(accrual.getStatus())) {
                accrual.setStatus("ACTIVE");
            }
            txRepo.save(accrual);

            restoredCount++;
            totalRestored = totalRestored.add(restoreAmount);

            log.debug("[Reversal] 批次还原: batchId={}, restored={}, newRemaining={}",
                    accrualTxId, restoreAmount, newRemaining);
        }

        // ---- Step 5: 标记原 REDEMPTION 流水为 REVERSED ----
        AccountTransaction redemptionTx = em.find(AccountTransaction.class, redemptionTransactionId);
        if (redemptionTx != null) {
            redemptionTx.setStatus("REVERSED");
            txRepo.save(redemptionTx);
        }

        log.info("[Reversal] REDEMPTION_CANCEL 完成: restored={}, expired={}, grace={}, "
                + "totalRestored={}, totalExpired={}",
                restoredCount, expiredCount, graceCount, totalRestored, totalExpired);
    }
}