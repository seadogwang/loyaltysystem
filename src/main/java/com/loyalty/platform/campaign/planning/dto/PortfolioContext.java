package com.loyalty.platform.campaign.planning.dto;

import com.loyalty.platform.domain.entity.campaign.*;
import lombok.*;

import java.util.List;

/**
 * Portfolio 上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioContext {
    private CampaignPortfolio portfolio;
    private List<CampaignPortfolioInitiativeRelation> initiativeRelations;
    private List<CampaignPortfolioKpi> kpis;
}
