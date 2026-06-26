package com.loyalty.platform.campaign.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 决策摘要 — 历史决策列表的轻量级条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionSummary {

    private String decisionId;
    private String workspaceId;
    private String portfolioId;
    private String decisionType;
    private String status;

    private BigDecimal totalBudget;
    private BigDecimal totalAllocated;
    private BigDecimal expectedTotalRoi;
    private Integer allocationCount;
    private Integer conflictsResolved;

    private String createdBy;
    private Instant createdAt;
    private Instant appliedAt;
}
