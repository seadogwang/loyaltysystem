package com.loyalty.platform.rules;

import com.loyalty.platform.accounting.PointGrantService;
import com.loyalty.platform.activity.ActivityStateService;
import com.loyalty.platform.activity.StepCycleCalculator;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.rules.action.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 动作执行器 — 统一接收规则输出的 Action 集合并在一个强事务中执行。
 *
 * <p><b>强制约束</b>：
 * <ul>
 *   <li>禁止在 ActionCollector 或 DRL 脚本的 then 块中直接调用任何 JPA/SQL 操作。</li>
 *   <li>禁止将 List&lt;Action&gt; 拆分为多个独立事务执行。</li>
 *   <li>若 executeRewards 内抛出异常，事务全部回滚，由上层调用方触发重试。</li>
 * </ul>
 *
 * <p><b>扩展性</b>：新增动作类型只需在 {@link #executeActions} 中添加
 * {@code if (action instanceof NewAction)} 分支，无需修改其他代码。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Service
public class RewardExecutor {

    private static final Logger log = LoggerFactory.getLogger(RewardExecutor.class);

    private final PointGrantService pointGrantService;
    private final ActivityStateService activityStateService;

    public RewardExecutor(PointGrantService pointGrantService, ActivityStateService activityStateService) {
        this.pointGrantService = pointGrantService;
        this.activityStateService = activityStateService;
    }

    /**
     * 在单一数据库强事务中执行规则产生的所有动作。
     * 任何一步失败，整个事务回滚。
     *
     * @param memberId 会员 ID（业务主键）
     * @param actions  规则引擎输出的动作列表
     * @throws BusinessException 如果执行失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeActions(Long memberId, List<Action> actions) {
        if (actions == null || actions.isEmpty()) {
            log.debug("[RewardExecutor] 无动作需要执行: member={}", memberId);
            return;
        }

        log.info("[RewardExecutor] 开始执行 {} 个动作: member={}", actions.size(), memberId);

        int executed = 0;
        for (Action action : actions) {
            try {
                if (action instanceof AwardPointsAction award) {
                    BigDecimal finalPoints = applyAccumulativeLimit(award);
                    if (finalPoints.compareTo(BigDecimal.ZERO) > 0) {
                        pointGrantService.grantPoints(
                                award.getProgramCode(),
                                Long.parseLong(award.getMemberId()),
                                award.getAccountType(),
                                finalPoints,
                                award.getRuleId(),
                                award.getRuleSnapshotId()
                        );
                        // Track cumulative reward
                        activityStateService.addRewarded(
                                award.getProgramCode(), award.getRuleId(), award.getMemberId(), finalPoints);
                        log.debug("[RewardExecutor] 发分: rule={}, member={}, points={} (original={})",
                                award.getRuleId(), award.getMemberId(), finalPoints, award.getPoints());
                    } else {
                        log.debug("[RewardExecutor] 累加上限已达，跳过发分: rule={}, member={}",
                                award.getRuleId(), award.getMemberId());
                    }
                    executed++;
                } else if (action instanceof UpgradeTierAction upgrade) {
                    executeUpgradeTier(upgrade);
                    executed++;
                } else if (action instanceof DowngradeTierAction downgrade) {
                    executeDowngradeTier(downgrade);
                    executed++;
                } else {
                    log.warn("[RewardExecutor] 未知动作类型: {}", action.getClass().getSimpleName());
                }
            } catch (Exception e) {
                log.error("[RewardExecutor] 动作执行失败，事务回滚: action={}", action, e);
                throw new BusinessException("ERR_REWARD_EXECUTION_FAILED",
                        "Reward execution failed at action: " + action.actionType(), e);
            }
        }

        log.info("[RewardExecutor] 执行完成: member={}, executed={}/{}", memberId, executed, actions.size());
    }

    /**
     * Apply accumulative limit check and excess strategy.
     * Returns the actual points to grant after applying caps.
     */
    private BigDecimal applyAccumulativeLimit(AwardPointsAction award) {
        BigDecimal accumulativeLimit = award.getAccumulativeLimit();
        BigDecimal theoreticalPoints = award.getPoints();

        if (accumulativeLimit == null) {
            return theoreticalPoints;
        }

        BigDecimal alreadyRewarded = activityStateService.getTotalRewarded(
                award.getProgramCode(), award.getRuleId(), award.getMemberId());
        BigDecimal remainingCap = accumulativeLimit.subtract(alreadyRewarded);

        if (remainingCap.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[RewardExecutor] 累加上限已用完: rule={}, member={}, limit={}, already={}",
                    award.getRuleId(), award.getMemberId(), accumulativeLimit, alreadyRewarded);
            return BigDecimal.ZERO;
        }

        if (theoreticalPoints.compareTo(remainingCap) <= 0) {
            return theoreticalPoints; // Within limit
        }

        // Exceeded — apply excess strategy
        String strategy = award.getExcessStrategy() != null ? award.getExcessStrategy() : "STOP";
        log.info("[RewardExecutor] 触发超限策略: rule={}, member={}, strategy={}, theoretical={}, remainingCap={}",
                award.getRuleId(), award.getMemberId(), strategy, theoreticalPoints, remainingCap);

        return switch (strategy) {
            case "STOP" -> remainingCap;
            case "RATIO" -> {
                BigDecimal ratio = remainingCap.divide(theoreticalPoints, 4, RoundingMode.HALF_UP);
                yield theoreticalPoints.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
            }
            case "TRUNCATE_AND_DOWNGRADE" -> {
                BigDecimal dwMult = award.getDowngradeMultiplier() != null
                        ? award.getDowngradeMultiplier() : BigDecimal.ONE;
                // Normal portion up to remaining capacity at full rate: remainingCap
                // Excess: (theoreticalPoints - remainingCap) * downgradeMultiplier
                BigDecimal excess = theoreticalPoints.subtract(remainingCap);
                if (excess.compareTo(BigDecimal.ZERO) > 0) {
                    yield remainingCap.add(excess.multiply(dwMult).setScale(4, RoundingMode.HALF_UP));
                }
                yield remainingCap;
            }
            default -> remainingCap;
        };
    }

    private void executeUpgradeTier(UpgradeTierAction upgrade) {
        log.info("[RewardExecutor] 等级升级: member={}, tier={}", upgrade.getMemberId(), upgrade.getNewTier());
        // 实际调用 TierEvaluationService.upgrade()
    }

    private void executeDowngradeTier(DowngradeTierAction downgrade) {
        log.info("[RewardExecutor] 等级降级: member={}, tier={}", downgrade.getMemberId(), downgrade.getNewTier());
        // 实际调用 TierEvaluationService.downgrade()
    }
}