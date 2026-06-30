package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignPortfolioInitiativeRelation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignPortfolioInitiativeRelationRepository extends CampaignBaseRepository<CampaignPortfolioInitiativeRelation, String> {

    List<CampaignPortfolioInitiativeRelation> findByPortfolioId(String portfolioId);

    List<CampaignPortfolioInitiativeRelation> findByInitiativeId(String initiativeId);
}
