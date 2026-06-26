package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePortfolioRequest {
    private String workspaceId;
    private String name;
    private String description;
    private String optimizationMode;  // ROI_MAXIMIZATION / REVENUE_MAXIMIZATION / BALANCED
    private BigDecimal totalBudget;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
