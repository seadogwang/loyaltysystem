package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignExecutionMaster;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignExecutionMasterRepository extends CampaignBaseRepository<CampaignExecutionMaster, String> {

    List<CampaignExecutionMaster> findByPlanIdOrderByStartTimeDesc(String planId);
    List<CampaignExecutionMaster> findByStatusOrderByStartTimeDesc(String status);
    Optional<CampaignExecutionMaster> findByExecutionKey(Long executionKey);
}
