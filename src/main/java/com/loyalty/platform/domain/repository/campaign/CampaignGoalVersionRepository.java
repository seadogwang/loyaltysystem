package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignGoalVersion;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignGoalVersionRepository extends BaseRepository<CampaignGoalVersion, String> {

    List<CampaignGoalVersion> findByGoalIdOrderByVersionDesc(String goalId);
}
