package com.loyalty.platform.campaign.opportunity.dto;

import lombok.*;

/**
 * 机会发现请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoverOpportunitiesRequest {
    private String workspaceId;
    private String goalId;
    @Builder.Default
    private int maxResults = 10000;
    @Builder.Default
    private boolean includeDetails = true;
}
