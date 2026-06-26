package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignPlan;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignPlanRepository extends BaseRepository<CampaignPlan, String> {

    List<CampaignPlan> findByWorkspaceId(String workspaceId);

    List<CampaignPlan> findByGoalId(String goalId);

    List<CampaignPlan> findByInitiativeId(String initiativeId);

    List<CampaignPlan> findByWorkspaceIdAndStatus(String workspaceId, String status);
}
