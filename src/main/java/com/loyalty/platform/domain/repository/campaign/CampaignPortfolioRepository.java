package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignPortfolio;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignPortfolioRepository extends BaseRepository<CampaignPortfolio, String> {

    List<CampaignPortfolio> findByWorkspaceId(String workspaceId);

    List<CampaignPortfolio> findByWorkspaceIdAndStatus(String workspaceId, String status);
}
