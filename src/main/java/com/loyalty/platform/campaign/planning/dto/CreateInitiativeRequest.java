package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInitiativeRequest {
    private String goalId;
    private String name;
    private String description;
    private String initiativeType;   // WINBACK / GROWTH / ENGAGEMENT / ACQUISITION
    private Integer priority;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> ruleConfig;
    private List<CreateKpiRequest> kpis;
}
