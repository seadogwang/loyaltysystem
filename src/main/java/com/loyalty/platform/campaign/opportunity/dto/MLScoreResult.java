package com.loyalty.platform.campaign.opportunity.dto;

import lombok.*;

/**
 * ML 模型预测结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLScoreResult {
    private String memberId;
    private Double churnProbability;
    private Double upliftScore;
    private Double conversionProbability;
    private Double confidence;

    /**
     * ML 服务降级时的默认值。
     */
    public static MLScoreResult fallback(String memberId) {
        return MLScoreResult.builder()
                .memberId(memberId)
                .churnProbability(0.3)
                .upliftScore(0.3)
                .conversionProbability(0.3)
                .confidence(0.5)
                .build();
    }
}
