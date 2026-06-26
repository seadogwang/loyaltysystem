package com.loyalty.platform.campaign.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationResultResponse {

    private String optimizationId;
    private String optimizationType;
    private String status;
    private BigDecimal expectedRoi;
    private BigDecimal expectedRevenue;
    private BigDecimal improvementPct;
    private int iterationCount;
    private long convergenceTimeMs;
    private BigDecimal baselineRoi;
    private List<AllocationDetail> allocationDetails;
    private Instant createdAt;

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
    }
}
