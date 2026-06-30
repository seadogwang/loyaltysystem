package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignExecutionUserDetail;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignExecutionUserDetailRepository extends CampaignBaseRepository<CampaignExecutionUserDetail, String> {

    List<CampaignExecutionUserDetail> findByExecutionIdAndNodeId(String executionId, String nodeId);
    List<CampaignExecutionUserDetail> findByUserIdOrderByExecutedAtDesc(String userId);
}
