package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateKpiRequest {
    private String kpiType;
    private BigDecimal targetValue;
    private BigDecimal weight;
}
