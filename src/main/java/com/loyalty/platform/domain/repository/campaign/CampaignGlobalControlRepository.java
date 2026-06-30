package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignGlobalControl;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignGlobalControlRepository extends CampaignBaseRepository<CampaignGlobalControl, String> {

    Optional<CampaignGlobalControl> findByProgramCode(String programCode);
}
