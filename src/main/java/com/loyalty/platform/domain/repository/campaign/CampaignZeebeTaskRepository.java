package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignZeebeTask;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignZeebeTaskRepository extends BaseRepository<CampaignZeebeTask, String> {

    List<CampaignZeebeTask> findByInstanceIdOrderByStartTimeDesc(String instanceId);

    List<CampaignZeebeTask> findByPlanIdOrderByStartTimeDesc(String planId);

    List<CampaignZeebeTask> findTop10ByPlanIdOrderByStartTimeDesc(String planId);

    List<CampaignZeebeTask> findByTaskTypeAndStatus(String taskType, String status);
}
