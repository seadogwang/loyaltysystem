package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignStrategyAdjustment;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignStrategyAdjustmentRepository extends CampaignBaseRepository<CampaignStrategyAdjustment, String> {

    List<CampaignStrategyAdjustment> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    List<CampaignStrategyAdjustment> findByPlanIdOrderByCreatedAtDesc(String planId);

    List<CampaignStrategyAdjustment> findByStatusOrderByCreatedAtDesc(String status);
}
