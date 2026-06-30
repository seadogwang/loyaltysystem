package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignSimulationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignSimulationResultRepository extends CampaignBaseRepository<CampaignSimulationResult, String> {

    Page<CampaignSimulationResult> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable pageable);

    Page<CampaignSimulationResult> findByGoalIdOrderByCreatedAtDesc(String goalId, Pageable pageable);

    Page<CampaignSimulationResult> findBySimulationTypeOrderByCreatedAtDesc(String simulationType, Pageable pageable);

    Optional<CampaignSimulationResult> findFirstByGoalIdOrderByCreatedAtDesc(String goalId);
}
