package com.loyalty.platform.campaign.decision.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * 单个预算分配项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationItem {
    private String candidateId;
    private String candidateName;
    private String initiativeId;
    private BigDecimal allocatedBudget;
    private BigDecimal expectedROI;
    private double priorityScore;
    private double percentage;
}
