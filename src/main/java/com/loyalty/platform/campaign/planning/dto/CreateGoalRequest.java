package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGoalRequest {
    private String workspaceId;
    private String name;
    private String description;
    private String goalType;         // REVENUE / RETENTION / ACQUISITION / ENGAGEMENT
    private String targetMetric;
    private BigDecimal targetValue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<CreateKpiRequest> kpis;
}
