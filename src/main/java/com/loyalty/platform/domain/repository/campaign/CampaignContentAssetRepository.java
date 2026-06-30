package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignContentAsset;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignContentAssetRepository extends CampaignBaseRepository<CampaignContentAsset, String> {

    List<CampaignContentAsset> findByProgramCode(String programCode);

    List<CampaignContentAsset> findByProgramCodeAndStatus(String programCode, String status);

    List<CampaignContentAsset> findByProgramCodeAndAssetType(String programCode, String assetType);

    List<CampaignContentAsset> findByCreatedBy(String createdBy);
}
