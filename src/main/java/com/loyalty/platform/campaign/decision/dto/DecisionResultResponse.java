package com.loyalty.platform.campaign.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 决策结果响应 — 决策引擎运行的完整输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResultResponse {

    private String decisionId;
    private String workspaceId;
    private String portfolioId;
    private String goalId;
    private String decisionType;
    private String status;

    private BigDecimal totalBudget;
    private BigDecimal totalAllocated;
    private BigDecimal expectedTotalRoi;
    private Integer conflictsResolved;
    private Integer rejectedCandidates;

    /** 预算分配明细 */
    private List<AllocationDetail> allocations;

    /** 仲裁摘要 */
    private ArbitrationSummary arbitrationSummary;

    private String createdBy;
    private Instant createdAt;
    private Instant appliedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationDetail {
        private String initiativeId;
        private String initiativeName;
        private BigDecimal allocatedBudget;
        private BigDecimal expectedRoi;
        private Double percentage;
        private Integer executionOrder;
        private Double priorityScore;
        private Long opportunityCount;
        private Long targetUserCount;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArbitrationSummary {
        private Integer userConflicts;
        private Integer budgetConflicts;
        private Integer channelConflicts;
        private Integer resolved;
    }
}
