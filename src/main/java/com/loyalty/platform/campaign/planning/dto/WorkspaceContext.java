package com.loyalty.platform.campaign.planning.dto;

import com.loyalty.platform.domain.entity.campaign.*;
import lombok.*;

import java.util.List;

/**
 * 工作区完整上下文 — AI 核心输入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceContext {
    private CampaignWorkspace workspace;
    private CampaignGoal activeGoal;
    private List<CampaignInitiative> initiatives;
    private List<CampaignPortfolio> portfolios;
}
