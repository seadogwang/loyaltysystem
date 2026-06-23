package com.loyalty.platform.accounting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 积分负分风险告警服务 — 监控和告警负余额情况。
 *
 * <p>告警触发场景：
 * <ol>
 *   <li>退款扣分后余额为负 → 调用 {@link #recordAlert}</li>
 *   <li>每 6 小时定时巡检 → 扫描所有负余额账户，对比透支上限</li>
 *   <li>持续负分 > 30 天 → 升级为 CRITICAL</li>
 * </ol>
 */
@Service
public class RiskAlertService {

    private static final Logger log = LoggerFactory.getLogger(RiskAlertService.class);

    @PersistenceContext
    private EntityManager em;

    // ======================== 实时告警 ========================

    /**
     * 记录扣减后的负分风险告警。
     *
     * @param programCode    租户代码
     * @param memberId       会员 ID
     * @param accountType    账户类型
     * @param currentBalance 当前余额（可能为负）
     * @param overdraftLimit 透支上限
     */
    @Transactional
    public void recordAlert(String programCode, Long memberId, String accountType,
                            BigDecimal currentBalance, BigDecimal overdraftLimit) {
        if (currentBalance == null || currentBalance.compareTo(BigDecimal.ZERO) >= 0) {
            return; // 余额非负，不告警
        }

        BigDecimal limit = overdraftLimit != null ? overdraftLimit : BigDecimal.ZERO;

        // 判断告警等级
        String level;
        String message;

        // Check if there's a previous alert still active (not acknowledged)
        List<RiskAlert> existingAlerts = em.createQuery(
                "SELECT a FROM RiskAlert a WHERE a.memberId = :memberId AND a.accountType = :accountType "
                        + "AND a.acknowledged = false ORDER BY a.createdAt DESC",
                RiskAlert.class)
                .setParameter("memberId", memberId)
                .setParameter("accountType", accountType)
                .setMaxResults(1)
                .getResultList();

        boolean longStanding = false;
        if (!existingAlerts.isEmpty()) {
            RiskAlert oldest = existingAlerts.get(0);
            long days = ChronoUnit.DAYS.between(oldest.getCreatedAt(), LocalDateTime.now());
            if (days > 30) {
                longStanding = true;
            }
        }

        BigDecimal negativeAmount = currentBalance.abs();

        if (longStanding) {
            level = "CRITICAL";
            message = String.format("持续负分超过30天: 余额=%.2f, 透支上限=%.2f, 超出=%.2f",
                    currentBalance, limit, negativeAmount.subtract(limit));
        } else if (negativeAmount.compareTo(limit) > 0) {
            level = "WARNING";
            message = String.format("余额超过透支上限: 余额=%.2f, 上限=%.2f, 超出=%.2f",
                    currentBalance, limit, negativeAmount.subtract(limit));
        } else {
            level = "INFO";
            message = String.format("负分扣减记录: 余额=%.2f, 透支上限=%.2f",
                    currentBalance, limit);
        }

        RiskAlert alert = RiskAlert.builder()
                .programCode(programCode)
                .memberId(memberId)
                .accountType(accountType)
                .currentBalance(currentBalance)
                .overdraftLimit(limit)
                .level(level)
                .message(message)
                .acknowledged(false)
                .createdAt(LocalDateTime.now())
                .build();

        em.persist(alert);
        log.warn("[RiskAlert] {} 告警: member={}, balance={}, limit={}, level={}",
                level, memberId, currentBalance, limit, level);
    }

    // ======================== 定时巡检 ========================

    /**
     * 每 6 小时定时扫描负余额账户，生成风险报告。
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional(readOnly = true)
    public void scheduledRiskScan() {
        log.info("[RiskAlert] 开始定时巡检负余额账户...");

        @SuppressWarnings("unchecked")
        List<Object[]> negativeAccounts = em.createNativeQuery(
                "SELECT program_code, member_id, account_type, current_balance, overdraft_limit "
                        + "FROM member_account WHERE current_balance < 0")
                .getResultList();

        int totalNegative = negativeAccounts.size();
        int warningCount = 0;
        int criticalCount = 0;

        for (Object[] row : negativeAccounts) {
            String programCode = (String) row[0];
            Long memberId = ((Number) row[1]).longValue();
            String accountType = (String) row[2];
            BigDecimal balance = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
            BigDecimal overdraft = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;

            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                recordAlert(programCode, memberId, accountType, balance, overdraft);

                BigDecimal negativeAmount = balance.abs();
                if (negativeAmount.compareTo(overdraft) > 0) {
                    warningCount++;
                }
            }
        }

        // 检查持续负分 > 30 天的升级 CRITICAL
        int upgraded = em.createQuery(
                "UPDATE RiskAlert a SET a.level = 'CRITICAL' "
                        + "WHERE a.acknowledged = false AND a.createdAt < :threshold AND a.level != 'CRITICAL'")
                .setParameter("threshold", LocalDateTime.now().minusDays(30))
                .executeUpdate();
        criticalCount += upgraded;

        log.info("[RiskAlert] 巡检完成: 负余额账户={}, WARNING={}, 新增CRITICAL={}",
                totalNegative, warningCount, criticalCount);
    }

    // ======================== 查询与确认 ========================

    /** 查询未确认的告警 */
    @Transactional(readOnly = true)
    public List<RiskAlert> getUnacknowledgedAlerts(String programCode) {
        return em.createQuery(
                "SELECT a FROM RiskAlert a WHERE a.programCode = :pc AND a.acknowledged = false "
                        + "ORDER BY CASE a.level WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END, a.createdAt DESC",
                RiskAlert.class)
                .setParameter("pc", programCode)
                .getResultList();
    }

    /** 确认告警 */
    @Transactional
    public void acknowledgeAlert(Long alertId, String acknowledgedBy) {
        RiskAlert alert = em.find(RiskAlert.class, alertId);
        if (alert != null) {
            alert.setAcknowledged(true);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alert.setAcknowledgedBy(acknowledgedBy);
            log.info("[RiskAlert] 告警已确认: id={}, by={}", alertId, acknowledgedBy);
        }
    }
}
