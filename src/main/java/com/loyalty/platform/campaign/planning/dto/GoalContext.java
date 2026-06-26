package com.loyalty.platform.campaign.planning.dto;

import com.loyalty.platform.domain.entity.campaign.CampaignGoal;
import com.loyalty.platform.domain.entity.campaign.CampaignGoalKpi;
import lombok.*;

import java.util.List;

/**
 * Goal 上下文（含 KPI）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalContext {
    private CampaignGoal goal;
    private List<CampaignGoalKpi> kpis;
    private Double progress;
}
