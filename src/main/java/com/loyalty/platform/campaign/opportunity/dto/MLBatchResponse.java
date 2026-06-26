package com.loyalty.platform.campaign.opportunity.dto;

import lombok.*;

import java.util.List;

/**
 * ML 批量响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLBatchResponse {
    private List<MLScoreResult> results;
    private String modelVersion;
    private long inferenceTimeMs;
}
