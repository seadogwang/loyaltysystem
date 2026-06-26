package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BindPlanRequest {
    private String planId;
    private String role;       // PRIMARY / SUPPORTING / EXPERIMENTAL
    private BigDecimal weight;
}
