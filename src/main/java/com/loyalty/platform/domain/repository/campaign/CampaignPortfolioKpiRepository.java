package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignPortfolioKpi;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignPortfolioKpiRepository extends BaseRepository<CampaignPortfolioKpi, String> {

    List<CampaignPortfolioKpi> findByPortfolioId(String portfolioId);
}
