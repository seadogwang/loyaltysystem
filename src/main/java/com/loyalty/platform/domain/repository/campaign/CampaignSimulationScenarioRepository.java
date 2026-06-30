package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignSimulationScenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignSimulationScenarioRepository extends CampaignBaseRepository<CampaignSimulationScenario, String> {

    Page<CampaignSimulationScenario> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable pageable);

    Page<CampaignSimulationScenario> findByGoalIdOrderByCreatedAtDesc(String goalId, Pageable pageable);
}
