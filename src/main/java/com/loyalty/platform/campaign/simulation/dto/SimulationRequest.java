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
public class SimulationRequest {

    private String workspaceId;
    private String goalId;
    private String name;
    private String segmentCode;
    private String channel;
    private String campaignId;
    private Double offerStrength;
    private Double offerMatch;
    private BigDecimal budget;
    private AdvancedConfig advancedConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdvancedConfig {
        private boolean useML;
        private int simulationCount;
        private int randomSeed;
    }
}
