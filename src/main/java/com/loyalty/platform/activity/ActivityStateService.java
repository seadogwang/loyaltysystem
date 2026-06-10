package com.loyalty.platform.activity;

import com.loyalty.platform.domain.entity.MemberActivityState;
import com.loyalty.platform.domain.entity.RuleDefinition;
import com.loyalty.platform.domain.repository.MemberActivityStateRepository;
import com.loyalty.platform.domain.repository.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity cumulative state service — tracks per-member reward totals for promo rules.
 */
@Service
public class ActivityStateService {

    private static final Logger log = LoggerFactory.getLogger(ActivityStateService.class);

    private final MemberActivityStateRepository stateRepo;
    private final RuleDefinitionRepository ruleRepo;

    public ActivityStateService(MemberActivityStateRepository stateRepo, RuleDefinitionRepository ruleRepo) {
        this.stateRepo = stateRepo;
        this.ruleRepo = ruleRepo;
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalRewarded(String programCode, String ruleCode, String memberId) {
        return stateRepo.findByRuleCodeAndMemberId(programCode, ruleCode, memberId)
                .map(MemberActivityState::getTotalRewarded)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public void addRewarded(String programCode, String ruleCode, String memberId, BigDecimal points) {
        MemberActivityState state = stateRepo.findByRuleCodeAndMemberId(programCode, ruleCode, memberId)
                .orElse(null);
        if (state == null) {
            state = MemberActivityState.builder()
                    .programCode(programCode)
                    .memberId(memberId)
                    .ruleCode(ruleCode)
                    .totalRewarded(points)
                    .lastUpdatedAt(LocalDateTime.now())
                    .build();
        } else {
            state.setTotalRewarded(state.getTotalRewarded().add(points));
            state.setLastUpdatedAt(LocalDateTime.now());
        }
        stateRepo.save(state);
    }

    @Transactional(readOnly = true)
    public boolean isActivityActive(String ruleCode, LocalDateTime eventTime) {
        return ruleRepo.findByRuleCode(null, ruleCode)
                .map(rule -> {
                    if (!"ACTIVE".equals(rule.getStatus())) return false;
                    LocalDateTime start = rule.getEffectiveStart();
                    LocalDateTime end = rule.getEffectiveEnd();
                    if (start != null && eventTime.isBefore(start)) return false;
                    return end == null || !eventTime.isAfter(end);
                })
                .orElse(false);
    }
}