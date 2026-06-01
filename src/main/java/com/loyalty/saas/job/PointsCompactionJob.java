package com.loyalty.saas.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分批次碎片整理任务 —— 凌晨低峰期运行。
 *
 * <p>当会员的 ACTIVE 流水批次超过阈值时，将多个非即将过期的同类型批次
 * 合并为一条新的合并批次流水，原批次标记为 EXHAUSTED。
 *
 * <p><b>合并规则</b>：
 * <ul>
 *   <li>只合并 expiresAt > 30天后 或无过期时间的批次</li>
 *   <li>合并后新批次 remainingAmount = SUM(原批次 remainingAmount)</li>
 *   <li>新批次 transaction_type = 'ACCRUAL', rule_code = 'COMPACTION'</li>
 *   <li>原批次标记为 EXHAUSTED</li>
 * </ul>
 *
 * <p><b>事务边界</b>：每个会员的合并操作在独立事务中完成，保证短事务。
 *
 * <p><b>租户隔离</b>：通过 {@link TenantAwareJob#forEachTenant} 保证每个租户独立处理。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class PointsCompactionJob extends TenantAwareJob {

    /** 触发合并的 ACTIVE 批次阈值 */
    private static final int COMPACTION_THRESHOLD = 50;
    /** 仅合并不在 30 天内过期的批次 */
    private static final int MIN_DAYS_BEFORE_EXPIRY = 30;

    @Override protected String getJobName() { return "PointsCompactionJob"; }

    /** 每天凌晨 4:00 执行（避开 TierEvaluation 的 3:00） */
    @Scheduled(cron = "0 0 4 * * ?")
    public void execute() {
        forEachTenant(this::compactTenant);
    }

    void compactTenant(String programCode) {
        // 1. 查找需要整理的会员（按 accountType 分组，HAVING COUNT(*) > THRESHOLD）
        @SuppressWarnings("unchecked")
        List<Object[]> candidates = em.createNativeQuery(
                "SELECT member_id, account_type, COUNT(*) as batch_count "
                        + "FROM account_transaction "
                        + "WHERE program_code = ? "
                        + "  AND status = 'ACTIVE' "
                        + "  AND remaining_amount > 0 "
                        + "  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP + interval '30 days') "
                        + "GROUP BY member_id, account_type "
                        + "HAVING COUNT(*) > ? "
                        + "ORDER BY batch_count DESC "
                        + "LIMIT 100",
                Object[].class)
                .setParameter(1, programCode)
                .setParameter(2, COMPACTION_THRESHOLD)
                .getResultList();

        log.info("[CompactionJob] 租户 [{}] 待整理会员: {} 个", programCode, candidates.size());

        for (Object[] row : candidates) {
            Long memberId = ((Number) row[0]).longValue();
            String accountType = (String) row[1];
            compactMemberBatches(programCode, memberId, accountType);
        }
    }

    /**
     * 整理单个会员的碎片批次 —— 独立事务，合并耗时短。
     */
    @Transactional
    void compactMemberBatches(String programCode, Long memberId, String accountType) {
        // 只取非即将过期的 ACTIVE 批次
        @SuppressWarnings("unchecked")
        List<Object[]> batches = em.createNativeQuery(
                "SELECT id, remaining_amount, expires_at FROM account_transaction "
                        + "WHERE program_code = ? AND member_id = ? AND account_type = ? "
                        + "  AND status = 'ACTIVE' AND remaining_amount > 0 "
                        + "  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP + interval '30 days') "
                        + "ORDER BY COALESCE(expires_at, '2099-12-31') ASC, created_at ASC "
                        + "FOR UPDATE",
                Object[].class)
                .setParameter(1, programCode)
                .setParameter(2, memberId)
                .setParameter(3, accountType)
                .getResultList();

        if (batches.size() < 2) return;

        // SUM 所有可合并批次的 remainingAmount
        BigDecimal totalRemaining = BigDecimal.ZERO;
        LocalDateTime earliestExpiry = null;

        for (Object[] batch : batches) {
            BigDecimal remaining = new BigDecimal(batch[1].toString());
            totalRemaining = totalRemaining.add(remaining);

            if (batch[2] != null) {
                LocalDateTime expiry = ((java.sql.Timestamp) batch[2]).toLocalDateTime();
                if (earliestExpiry == null || expiry.isBefore(earliestExpiry)) {
                    earliestExpiry = expiry;
                }
            }
        }

        // 标记原批次为 EXHAUSTED
        for (Object[] batch : batches) {
            Long batchId = ((Number) batch[0]).longValue();
            em.createNativeQuery(
                    "UPDATE account_transaction SET remaining_amount = 0, status = 'EXHAUSTED' WHERE id = ?")
                    .setParameter(1, batchId)
                    .executeUpdate();
        }

        // 创建合并后的新批次
        BigDecimal compactedAmount = totalRemaining.setScale(4, RoundingMode.HALF_UP);
        em.createNativeQuery(
                "INSERT INTO account_transaction (program_code, member_id, account_type, "
                        + "transaction_type, amount, remaining_amount, expires_at, status, "
                        + "rule_code, operation_key, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                .setParameter(1, programCode)
                .setParameter(2, memberId)
                .setParameter(3, accountType)
                .setParameter(4, "ACCRUAL")
                .setParameter(5, compactedAmount)
                .setParameter(6, compactedAmount)
                .setParameter(7, earliestExpiry)
                .setParameter(8, "ACTIVE")
                .setParameter(9, "COMPACTION")
                .setParameter(10, programCode + ":COMPACTION:" + memberId + ":" + System.currentTimeMillis())
                .setParameter(11, LocalDateTime.now())
                .executeUpdate();

        log.debug("[CompactionJob] 会员 {} type={}: {} 批次 → 1, 总额={}",
                memberId, accountType, batches.size(), compactedAmount);
    }
}