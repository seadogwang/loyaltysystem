package com.loyalty.platform.rules;

import com.loyalty.platform.accounting.PointGrantService;
import com.loyalty.platform.activity.ActivityStateService;
import com.loyalty.platform.activity.StepCycleCalculator;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.common.util.ExpiryCalculator;
import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.domain.entity.TierChangeLog;
import com.loyalty.platform.domain.entity.TierDefinition;
import com.loyalty.platform.domain.repository.MemberRepository;
import com.loyalty.platform.rules.action.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final MemberRepository memberRepo;

    @PersistenceContext
    private EntityManager em;

    public RewardExecutor(PointGrantService pointGrantService, ActivityStateService activityStateService,
                          MemberRepository memberRepo) {
        this.pointGrantService = pointGrantService;
        this.activityStateService = activityStateService;
        this.memberRepo = memberRepo;
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
                                parseMemberId(award.getMemberId()),
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
                } else if (action instanceof IncrementCounterAction counter) {
                    executeIncrementCounter(counter);
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
     * 安全解析 memberId 字符串为 Long — 提供清晰的错误信息而非隐式 NumberFormatException。
     */
    private Long parseMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new BusinessException("ERR_INVALID_MEMBER_ID", "memberId 为空，无法执行动作");
        }
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException e) {
            throw new BusinessException("ERR_INVALID_MEMBER_ID",
                    "memberId 格式错误: '" + memberId + "' — 期望数字格式");
        }
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
        Long memberId = parseMemberId(upgrade.getMemberId());
        String newTier = upgrade.getNewTier();
        log.info("[RewardExecutor] 等级升级: member={}, tier={} → {}", memberId, upgrade.getMemberId(), newTier);

        // 获取当前租户上下文
        String pc = com.loyalty.platform.common.context.TenantContext.get();
        if (pc == null) {
            log.error("[RewardExecutor] 租户上下文缺失，无法执行等级升级");
            return;
        }

        Member member = memberRepo.findByMemberId(pc, memberId)
                .orElseThrow(() -> new BusinessException("ERR_MEMBER_NOT_FOUND",
                        "Member not found: " + memberId));
        String oldTier = member.getTierCode();

        // 更新会员等级和有效期
        LocalDateTime now = LocalDateTime.now();
        member.setTierCode(newTier);
        member.setTierEffectiveFrom(now);

        // 从 tier_definition 读取等级有效期配置
        String validityMode = "CALENDAR_YEARS";
        int validityValue = 1;
        try {
            TierDefinition tierDef = em.createQuery(
                "SELECT t FROM TierDefinition t WHERE t.programCode=:pc AND t.tierCode=:tc",
                TierDefinition.class)
                .setParameter("pc", pc).setParameter("tc", newTier)
                .getSingleResult();
            if (tierDef.getUpgradeCriteria() != null) {
                if (tierDef.getUpgradeCriteria().get("validity_mode") instanceof String s) validityMode = s;
                if (tierDef.getUpgradeCriteria().get("validity_value") instanceof Number n) validityValue = n.intValue();
            }
        } catch (Exception e) {
            log.debug("[RewardExecutor] 无法读取等级有效期配置，使用默认值: {}", e.getMessage());
        }
        member.setTierExpiresAt(validityValue > 0 ? calculateTierExpiry(validityMode, validityValue) : null);
        member.setUpdatedAt(now);
        memberRepo.save(member);

        // 写入等级变更日志
        TierChangeLog tLog = new TierChangeLog();
        tLog.setProgramCode(pc);
        tLog.setMemberId(memberId);
        tLog.setFromTier(oldTier);
        tLog.setToTier(newTier);
        tLog.setChangeReason(upgrade.getReason());
        tLog.setChangedAt(now);
        em.persist(tLog);

        log.info("[RewardExecutor] 等级升级完成: member={}, {}→{}, expiresAt={}",
                memberId, oldTier, newTier, member.getTierExpiresAt());
    }

    private void executeDowngradeTier(DowngradeTierAction downgrade) {
        Long memberId = parseMemberId(downgrade.getMemberId());
        String newTier = downgrade.getNewTier();
        log.info("[RewardExecutor] 等级降级: member={}, tier={}", memberId, newTier);

        String pc = com.loyalty.platform.common.context.TenantContext.get();
        if (pc == null) {
            log.error("[RewardExecutor] 租户上下文缺失，无法执行等级降级");
            return;
        }

        Member member = memberRepo.findByMemberId(pc, memberId)
                .orElseThrow(() -> new BusinessException("ERR_MEMBER_NOT_FOUND",
                        "Member not found: " + memberId));
        String oldTier = member.getTierCode();

        // 更新会员等级，清除有效期
        LocalDateTime now = LocalDateTime.now();
        member.setTierCode(newTier);
        member.setTierEffectiveFrom(null);
        member.setTierExpiresAt(null);
        member.setUpdatedAt(now);
        memberRepo.save(member);

        // 写入等级变更日志
        TierChangeLog tLog = new TierChangeLog();
        tLog.setProgramCode(pc);
        tLog.setMemberId(memberId);
        tLog.setFromTier(oldTier);
        tLog.setToTier(newTier);
        tLog.setChangeReason(downgrade.getReason());
        tLog.setChangedAt(now);
        em.persist(tLog);

        log.info("[RewardExecutor] 等级降级完成: member={}, {}→{}", memberId, oldTier, newTier);
    }

    /**
     * 执行计数器动作 — 更新 member.ext_attributes 中的变量值。
     *
     * <p>设计文档 §4.2: 变量存储在 member.ext_attributes JSONB 中。
     * 计数器是用户自定义的动态变量，每次规则触发时按步长累加或递减。
     */
    private void executeIncrementCounter(IncrementCounterAction counter) {
        Long memberId = parseMemberId(counter.getMemberId());
        String counterName = counter.getCounterName();
        String pc = counter.getProgramCode();

        Member member = memberRepo.findByMemberId(pc, memberId)
                .orElseThrow(() -> new BusinessException("ERR_MEMBER_NOT_FOUND",
                        "Member not found: " + memberId));

        Map<String, Object> ext = member.getExtAttributes();
        if (ext == null) ext = new java.util.LinkedHashMap<>();

        double currentValue = counter.getStartValue();
        Object existing = ext.get(counterName);
        if (existing instanceof Number) {
            currentValue = ((Number) existing).doubleValue();
        }

        double newValue;
        if ("-".equals(counter.getOperator())) {
            newValue = currentValue - counter.getStep();
        } else {
            newValue = currentValue + counter.getStep();
        }

        ext.put(counterName, newValue);
        member.setExtAttributes(ext);
        member.setUpdatedAt(LocalDateTime.now());
        memberRepo.save(member);

        log.info("[RewardExecutor] 计数器更新: member={}, {} {} {} = {}→{}",
                memberId, counterName, counter.getOperator(), counter.getStep(), currentValue, newValue);
    }

    /**
     * 计算等级过期时间 — 委托给 {@link ExpiryCalculator}，与积分过期使用同一算法。
     */
    private LocalDateTime calculateTierExpiry(String mode, int value) {
        return ExpiryCalculator.calculateExpiry(LocalDateTime.now(), mode, value);
    }
}