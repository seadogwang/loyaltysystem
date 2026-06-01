package com.loyalty.saas.job;

import com.loyalty.saas.common.context.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员等级保降级任务 — 每天凌晨执行。
 *
 * <p>扫描 member_tier 表中 next_evaluation_date <= NOW() 的会员，
 * 提取过去一个周期的成长值，判断是否满足保级条件。不满足则向下逐级回退。
 *
 * <p><b>租户隔离</b>：继承 {@link TenantAwareJob}，通过 {@code forEachTenant}
 * 在每个租户的处理前后严格 {@code set()/clear()} TenantContext。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class TierEvaluationJob extends TenantAwareJob {

    /** cron: 每天凌晨 3:00 执行 */
    private static final String CRON = "0 0 3 * * ?";

    @Override
    protected String getJobName() { return "TierEvaluationJob"; }

    /**
     * 每日凌晨触发等级保降级评估。
     */
    @Scheduled(cron = CRON)
    public void execute() {
        if (log.isDebugEnabled()) {
            log.debug("[TierEvaluationJob] 触发定时任务");
        }
        forEachTenant(this::evaluateTenant);
    }

    /**
     * 对单个租户执行等级评估。
     */
    @Transactional
    void evaluateTenant(String programCode) {
        // 查询需要评估的会员
        @SuppressWarnings("unchecked")
        List<Object[]> dueMembers = em.createNativeQuery(
                "SELECT m.program_code, m.member_id, mt.current_tier, mt.next_evaluation_date, "
                        + "COALESCE(SUM(at2.remaining_amount), 0) AS tier_points "
                        + "FROM member_tier mt "
                        + "JOIN member m ON mt.program_code = m.program_code AND mt.member_id = m.member_id "
                        + "LEFT JOIN account_transaction at2 ON at2.program_code = mt.program_code "
                        + "  AND at2.member_id = mt.member_id "
                        + "  AND at2.account_type = 'TIER_POINTS' "
                        + "  AND at2.status = 'ACTIVE' AND at2.remaining_amount > 0 "
                        + "WHERE mt.program_code = ? "
                        + "  AND mt.next_evaluation_date <= CURRENT_DATE "
                        + "  AND m.status = 'ENROLLED' "
                        + "GROUP BY m.program_code, m.member_id, mt.current_tier, mt.next_evaluation_date "
                        + "LIMIT 500",
                Object[].class)
                .setParameter(1, programCode)
                .getResultList();

        log.info("[TierEvaluationJob] 租户 [{}] 待评估会员: {} 个", programCode, dueMembers.size());

        int downgradeCount = 0;

        for (Object[] row : dueMembers) {
            Long memberId = ((Number) row[1]).longValue();
            String currentTier = (String) row[2];
            BigDecimal tierPoints = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

            // 简化：按成长值阶梯判定（实际应从 program.config_json 的 tier_matrix 读取）
            String newTier = evaluateTier(tierPoints);

            if (!newTier.equals(currentTier)) {
                // 写入等级变更记录
                em.createNativeQuery(
                        "INSERT INTO tier_change_log (program_code, member_id, from_tier, to_tier, "
                                + "change_reason, event_id, changed_at) VALUES (?,?,?,?,?,?,?)")
                        .setParameter(1, programCode)
                        .setParameter(2, memberId)
                        .setParameter(3, currentTier)
                        .setParameter(4, newTier)
                        .setParameter(5, newTier.compareTo(currentTier) > 0 ? "ORDER_ACCRUAL" : "SCHEDULED_EVALUATION")
                        .setParameter(6, "TIER_JOB_" + System.currentTimeMillis())
                        .setParameter(7, LocalDateTime.now())
                        .executeUpdate();

                // 更新 member_tier
                em.createNativeQuery(
                        "UPDATE member_tier SET current_tier = ?, effective_date = ?, "
                                + "next_evaluation_date = ?, updated_at = NOW() "
                                + "WHERE program_code = ? AND member_id = ?")
                        .setParameter(1, newTier)
                        .setParameter(2, LocalDate.now())
                        .setParameter(3, LocalDate.now().plusMonths(3))
                        .setParameter(4, programCode)
                        .setParameter(5, memberId)
                        .executeUpdate();

                downgradeCount++;
                log.debug("[TierEvaluationJob] 会员 {} 等级变更: {} → {}", memberId, currentTier, newTier);
            } else {
                // 保级成功，更新下次评估日期
                em.createNativeQuery(
                        "UPDATE member_tier SET next_evaluation_date = ?, updated_at = NOW() "
                                + "WHERE program_code = ? AND member_id = ?")
                        .setParameter(1, LocalDate.now().plusMonths(3))
                        .setParameter(2, programCode)
                        .setParameter(3, memberId)
                        .executeUpdate();
            }
        }

        if (downgradeCount > 0) {
            log.info("[TierEvaluationJob] 租户 [{}] 等级变更: {} 个", programCode, downgradeCount);
        }
    }

    /**
     * 简化等级评估：按成长值阶梯判定。
     * 实际应从 program.config_json.tier_matrix 动态读取。
     */
    private String evaluateTier(BigDecimal points) {
        if (points.compareTo(new BigDecimal("10000")) >= 0) return "PLATINUM";
        if (points.compareTo(new BigDecimal("5000")) >= 0) return "GOLD";
        if (points.compareTo(new BigDecimal("1000")) >= 0) return "SILVER";
        return "BASE";
    }
}