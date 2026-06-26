package com.loyalty.platform.campaign.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationRequest {

    private String portfolioId;
    private String optimizationType;  // GREEDY / GENETIC
    private OptimizationConstraints constraints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationConstraints {
        private BigDecimal maxBudget;
        private BigDecimal minInitiativeBudget;
        private BigDecimal maxInitiativeBudget;
        private Map<String, Integer> channelCapacity;
        private int maxGenerations;
        private int populationSize;
    }
}
