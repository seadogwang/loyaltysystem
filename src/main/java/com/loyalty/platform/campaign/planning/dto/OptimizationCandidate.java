package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * 优化候选 — 用于 Portfolio 预算分配算法。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationCandidate {
    private String initiativeId;
    private String initiativeName;
    private Integer priority;
    private BigDecimal expectedROI;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
}
