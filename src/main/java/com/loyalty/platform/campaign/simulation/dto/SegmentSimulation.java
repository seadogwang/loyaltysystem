package com.loyalty.platform.campaign.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentSimulation {

    private long members;
    private long conversions;
    private double conversionRate;
    private double totalProbability;

    public void addMember(double prob) {
        members++;
        totalProbability += prob;
        if (prob > 0.5) conversions++;
        conversionRate = members > 0 ? (double) conversions / members : 0;
    }
}
