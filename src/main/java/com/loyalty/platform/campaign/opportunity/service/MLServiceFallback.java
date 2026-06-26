package com.loyalty.platform.campaign.opportunity.service;

import com.loyalty.platform.campaign.opportunity.dto.MLScoreResult;
import com.loyalty.platform.campaign.opportunity.entity.CampaignMemberDim;
import org.springframework.stereotype.Component;

/**
 * ML 服务降级策略 — 当 ML 服务不可用时使用规则驱动的降级评分。
 */
@Component
public class MLServiceFallback {

    /**
     * 基于简单规则计算降级评分。
     */
    public MLScoreResult fallbackScore(CampaignMemberDim member) {
        double churnProb = 0.3;
        if (member.getRecencyDays() != null && member.getRecencyDays() > 60) {
            churnProb += 0.3;
        }
        if (member.getTotalOrderCount() != null && member.getTotalOrderCount() < 3) {
            churnProb += 0.2;
        }

        double uplift = 0.4;
        if (member.getTotalOrderAmount() != null
                && member.getTotalOrderAmount().doubleValue() > 5000) {
            uplift += 0.3;
        }

        return MLScoreResult.builder()
                .memberId(member.getMemberId())
                .churnProbability(Math.min(churnProb, 1.0))
                .upliftScore(Math.min(uplift, 1.0))
                .conversionProbability(0.4)
                .confidence(0.5)  // 低置信度
                .build();
    }
}
