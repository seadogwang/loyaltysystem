package com.loyalty.platform.campaign.decision.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * 营销活动候选 — 用于预算分配和冲突仲裁。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignCandidate {
    private String id;
    private String name;
    private String initiativeId;

    // 预算
    private BigDecimal recommendedBudget;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private BigDecimal expectedROI;

    // 评分（来自 Opportunity Engine）
    private double opportunityScore;
    private double strategicWeight;
    private double recencyBoost;

    // 渠道
    private String channel;
    private String segment;
}
