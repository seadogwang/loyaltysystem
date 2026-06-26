package com.loyalty.platform.campaign.planning.dto;

import com.loyalty.platform.domain.entity.campaign.*;
import lombok.*;

import java.util.List;

/**
 * Initiative 上下文（含 KPI 和绑定的 Plans）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiativeContext {
    private CampaignInitiative initiative;
    private List<CampaignInitiativeKpi> kpis;
    private List<CampaignInitiativePlanRelation> planRelations;
}
