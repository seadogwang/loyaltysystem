package com.loyalty.platform.campaign.opportunity.dto;

import lombok.*;

import java.util.List;

/**
 * ML 批量请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLBatchRequest {
    private List<MemberFeature> members;
    private String modelType;
}
