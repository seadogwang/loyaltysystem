package com.loyalty.platform.campaign.decision.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * What-if 模拟对比结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationCompareResult {
    private String planAId;
    private String planBId;
    private String planAName;
    private String planBName;
    private List<SimulationResult> planAResults;
    private List<SimulationResult> planBResults;
    private Map<String, Object> comparison;  // ROI对比、胜率等
}
