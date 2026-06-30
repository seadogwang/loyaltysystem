package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignExecutionStep;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignExecutionStepRepository extends CampaignBaseRepository<CampaignExecutionStep, String> {

    List<CampaignExecutionStep> findByExecutionIdOrderByStartTimeAsc(String executionId);
    Optional<CampaignExecutionStep> findByExecutionIdAndNodeId(String executionId, String nodeId);
    List<CampaignExecutionStep> findByExecutionIdAndStatusIn(String executionId, List<String> statuses);
}
