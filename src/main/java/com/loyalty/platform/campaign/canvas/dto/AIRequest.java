package com.loyalty.platform.campaign.canvas.dto;

import lombok.*;

/**
 * AI DAG 生成请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRequest {
    private String goal;
    private String description;
    private String budget;
    private String audience;
    private String channel;
    private String additionalInstructions;
}
