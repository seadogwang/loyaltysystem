package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignInitiativePlanRelation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignInitiativePlanRelationRepository extends BaseRepository<CampaignInitiativePlanRelation, String> {

    List<CampaignInitiativePlanRelation> findByInitiativeId(String initiativeId);

    List<CampaignInitiativePlanRelation> findByPlanId(String planId);

    void deleteByInitiativeIdAndPlanId(String initiativeId, String planId);
}
