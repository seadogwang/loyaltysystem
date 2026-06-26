package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignVariableBinding;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignVariableBindingRepository extends BaseRepository<CampaignVariableBinding, String> {

    List<CampaignVariableBinding> findByProgramCodeAndAssetIdOrderByPriorityAsc(String programCode, String assetId);

    List<CampaignVariableBinding> findByProgramCodeAndPlanIdOrderByPriorityAsc(String programCode, String planId);

    List<CampaignVariableBinding> findByProgramCodeAndSegmentCodeOrderByPriorityAsc(String programCode, String segmentCode);
}
