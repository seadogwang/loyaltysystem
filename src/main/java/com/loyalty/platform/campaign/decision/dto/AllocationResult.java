package com.loyalty.platform.campaign.decision.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算分配结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationResult {
    private String portfolioId;
    private BigDecimal totalBudget;
    private BigDecimal totalExpectedROI;
    private List<AllocationItem> allocations;
}
