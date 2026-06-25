package com.loyalty.platform.rules;

import com.loyalty.platform.common.event.EventBridge;
import com.loyalty.platform.common.util.ExpiryCalculator;
import com.loyalty.platform.domain.entity.*;
import com.loyalty.platform.domain.repository.*;
import com.loyalty.platform.rules.MemberVariableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 等级评估服务 — 事件驱动的升级/降级/保级评估引擎。
 *
 * <p>触发方式：
 * <ul>
 *   <li>事件触发：积分发放后实时评估升级</li>
 *   <li>定时任务：每日凌晨评估保级/降级</li>
 *   <li>手动触发：运营后台手动执行</li>
 * </ul>
 */
@Service
public class TierEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(TierEvaluationService.class);

    private final MemberRepository memberRepo;
    private final TierDefinitionRepository tierRepo;
    private final RuleDefinitionRepository ruleRepo;
    private final TierChangeLogRepository tierLogRepo;
    private final MemberVariableService variableService;
    private final TierActivityRepository tierActivityRepo;
    private final MemberTierActivityLogRepository tierActivityLogRepo;
    private final EventBridge eventBridge;

    public TierEvaluationService(MemberRepository memberRepo, TierDefinitionRepository tierRepo,
                                  RuleDefinitionRepository ruleRepo, TierChangeLogRepository tierLogRepo,
                                  MemberVariableService variableService,
                                  TierActivityRepository tierActivityRepo,
                                  MemberTierActivityLogRepository tierActivityLogRepo,
                                  EventBridge eventBridge) {
        this.memberRepo = memberRepo;
        this.tierRepo = tierRepo;
        this.ruleRepo = ruleRepo;
        this.tierLogRepo = tierLogRepo;
        this.variableService = variableService;
        this.tierActivityRepo = tierActivityRepo;
        this.tierActivityLogRepo = tierActivityLogRepo;
        this.eventBridge = eventBridge;
    }

    /**
     * 评估会员等级（升级/降级/保级）。
     */
    @Transactional
    public void evaluateMemberTier(String programCode, Long memberId, String evalType, String triggerEventId) {
        Member member = memberRepo.findByMemberId(programCode, memberId).orElse(null);
        if (member == null) return;

        String currentTier = member.getTierCode();

        // 1. 检查等级直升活动（最高优先级）
        if ("UPGRADE".equals(evalType)) {
            TierActivity matchedActivity = checkTierActivity(programCode, memberId);
            if (matchedActivity != null) {
                performTierUpgrade(member, matchedActivity.getTargetTierCode(), "ACTIVITY_UPGRADE", triggerEventId);
                return;
            }
        }

        // 2. 升级评估
        if ("UPGRADE".equals(evalType)) {
            List<RuleDefinition> upgradeRules = ruleRepo.findByProgramCodeAndRulePurpose(programCode, "TIER_UPGRADE");
            for (RuleDefinition rule : upgradeRules) {
                Map<String, Object> metadata = rule.getMetadata();
                if (metadata == null) continue;
                String targetTier = (String) metadata.get("tier_target");
                if (targetTier == null || targetTier.equals(currentTier)) continue;
                if (evaluateRule(programCode, memberId, rule)) {
                    performTierUpgrade(member, targetTier, "UPGRADE", triggerEventId);
                    return;
                }
            }
        }

        // 3. 保级/降级评估
        if ("RETENTION".equals(evalType)) {
            List<RuleDefinition> retentionRules = ruleRepo.findByProgramCodeAndRulePurpose(programCode, "TIER_RETENTION");
            for (RuleDefinition rule : retentionRules) {
                Map<String, Object> metadata = rule.getMetadata();
                if (metadata == null) continue;
                String tierSource = (String) metadata.get("tier_source");
                if (tierSource == null || !tierSource.equals(currentTier)) continue;
                if (!evaluateRule(programCode, memberId, rule)) {
                    String downgradeTarget = (String) metadata.get("downgrade_target");
                    if (downgradeTarget != null) {
                        performTierDowngrade(member, downgradeTarget, "DOWNGRADE", null);
                    }
                }
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateRule(String programCode, Long memberId, RuleDefinition rule) {
        Map<String, Object> metadata = rule.getMetadata();
        if (metadata == null) return false;
        Map<String, Object> eval = (Map<String, Object>) metadata.get("evaluation");
        if (eval == null) return false;

        String dimension = (String) eval.get("dimension");
        String operator = (String) eval.getOrDefault("operator", "AND");
        Integer windowDays = eval.get("timeWindowDays") instanceof Number n ? n.intValue() : null;
        BigDecimal actualValue = variableService.getDimensionValue(programCode, memberId, dimension, windowDays);
        BigDecimal requiredValue = toBigDecimal(eval.get("required_value"));

        boolean mainCondition = evaluateComparison(actualValue, eval.getOrDefault("op", ">=").toString(), requiredValue);
        if (!mainCondition) return false;

        List<Map<String, Object>> extraConditions = (List<Map<String, Object>>) eval.get("extra_conditions");
        if (extraConditions != null) {
            for (Map<String, Object> cond : extraConditions) {
                String condDim = (String) cond.get("dimension");
                BigDecimal condValue = variableService.getDimensionValue(programCode, memberId, condDim, windowDays);
                BigDecimal condRequired = toBigDecimal(cond.get("value"));
                String condOp = (String) cond.getOrDefault("operator", ">=");
                if (!evaluateComparison(condValue, condOp, condRequired)) return false;
            }
        }
        return true;
    }

    private boolean evaluateComparison(BigDecimal actual, String op, BigDecimal required) {
        if (actual == null || required == null) return false;
        return switch (op) {
            case ">=" -> actual.compareTo(required) >= 0;
            case ">" -> actual.compareTo(required) > 0;
            case "<" -> actual.compareTo(required) < 0;
            case "<=" -> actual.compareTo(required) <= 0;
            case "==" -> actual.compareTo(required) == 0;
            default -> actual.compareTo(required) >= 0;
        };
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s) return new BigDecimal(s);
        return BigDecimal.ZERO;
    }

    private void performTierUpgrade(Member member, String targetTier, String reason, String triggerEventId) {
        String oldTier = member.getTierCode();
        if (oldTier.equals(targetTier)) {
            // 同等级直升：延长有效期
            extendTierValidity(member, reason);
            return;
        }
        member.setTierCode(targetTier);
        memberRepo.save(member);
        tierLogRepo.save(TierChangeLog.builder()
                .programCode(member.getProgramCode()).memberId(member.getMemberId())
                .fromTier(oldTier).toTier(targetTier)
                .changeReason(reason).changedAt(LocalDateTime.now()).build());
        log.info("[TierEval] 会员 {} 升级: {} → {}", member.getMemberId(), oldTier, targetTier);
    }

    /**
     * 同等级直升：延长有效期。等级不变，有效期重置为当前时间 + 配置的周期。
     */
    private void extendTierValidity(Member member, String reason) {
        // 从 tier_definition 读取有效期配置
        TierDefinition tierDef = tierRepo.findByProgramCodeAndTierCode(
                member.getProgramCode(), member.getTierCode()).orElse(null);
        if (tierDef != null && tierDef.getUpgradeCriteria() != null) {
            String mode = (String) tierDef.getUpgradeCriteria().getOrDefault("validity_mode", "CALENDAR_YEARS");
            Integer value = (Integer) tierDef.getUpgradeCriteria().getOrDefault("validity_value", 1);
            if (value != null && value > 0) {
                LocalDateTime newExpiry = ExpiryCalculator.calculateExpiry(LocalDateTime.now(), mode, value);
                member.setTierExpiresAt(newExpiry);
                log.info("[TierEval] 会员 {} 等级续期: {}, 新有效期: {}", member.getMemberId(), member.getTierCode(), newExpiry);
            }
        }
        member.setTierEffectiveFrom(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        memberRepo.save(member);
        // 记录续期日志
        tierLogRepo.save(TierChangeLog.builder()
                .programCode(member.getProgramCode()).memberId(member.getMemberId())
                .fromTier(member.getTierCode()).toTier(member.getTierCode())
                .changeReason("RENEWAL").changedAt(LocalDateTime.now()).build());
    }

    private void performTierDowngrade(Member member, String targetTier, String reason, String triggerEventId) {
        String oldTier = member.getTierCode();
        if (oldTier.equals(targetTier)) return;
        member.setTierCode(targetTier);
        memberRepo.save(member);
        tierLogRepo.save(TierChangeLog.builder()
                .programCode(member.getProgramCode()).memberId(member.getMemberId())
                .fromTier(oldTier).toTier(targetTier)
                .changeReason(reason).changedAt(LocalDateTime.now()).build());
        log.info("[TierEval] 会员 {} 降级: {} → {}", member.getMemberId(), oldTier, targetTier);
    }

    private TierActivity checkTierActivity(String programCode, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        List<TierActivity> activities = tierActivityRepo.findActive(programCode, now);
        for (TierActivity activity : activities) {
            if (Boolean.TRUE.equals(activity.getOncePerMember())) {
                if (tierActivityLogRepo.existsByMemberIdAndActivityCode(memberId.toString(), activity.getActivityCode()))
                    continue;
            }
            return activity;
        }
        return null;
    }
}