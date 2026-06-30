package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignContentAssetHistory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignContentAssetHistoryRepository extends CampaignBaseRepository<CampaignContentAssetHistory, String> {

    List<CampaignContentAssetHistory> findByAssetIdOrderByVersionDesc(String assetId);
}
