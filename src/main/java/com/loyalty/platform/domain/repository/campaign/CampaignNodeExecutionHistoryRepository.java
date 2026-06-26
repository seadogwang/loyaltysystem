package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignNodeExecutionHistory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignNodeExecutionHistoryRepository extends BaseRepository<CampaignNodeExecutionHistory, String> {

    List<CampaignNodeExecutionHistory> findByPlanIdOrderByStartTimeDesc(String planId);

    List<CampaignNodeExecutionHistory> findByNodeIdOrderByStartTimeDesc(String nodeId);

    List<CampaignNodeExecutionHistory> findByStatusOrderByStartTimeDesc(String status);
}
