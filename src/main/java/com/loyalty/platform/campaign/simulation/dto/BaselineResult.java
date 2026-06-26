package com.loyalty.platform.campaign.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineResult {

    private String segmentCode;
    private long totalMembers;
    private long convertedMembers;
    private double conversionRate;
    private double avgOrderValue;
    private double estimatedRevenue;
    private int periodDays;
    private Map<String, Double> segmentBreakdown;
    private Instant calculatedAt;

    public static BaselineResult empty() {
        return BaselineResult.builder()
                .segmentCode("UNKNOWN")
                .totalMembers(0)
                .convertedMembers(0)
                .conversionRate(0)
                .avgOrderValue(0)
                .estimatedRevenue(0)
                .periodDays(30)
                .segmentBreakdown(Map.of())
                .calculatedAt(Instant.now())
                .build();
    }
}
