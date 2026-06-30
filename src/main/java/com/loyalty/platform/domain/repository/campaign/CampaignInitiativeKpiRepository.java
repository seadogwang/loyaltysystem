package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignInitiativeKpi;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignInitiativeKpiRepository extends CampaignBaseRepository<CampaignInitiativeKpi, String> {

    List<CampaignInitiativeKpi> findByInitiativeId(String initiativeId);

    Optional<CampaignInitiativeKpi> findByInitiativeIdAndKpiType(String initiativeId, String kpiType);
}
