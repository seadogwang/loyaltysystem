package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignOptimizationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignOptimizationResultRepository extends BaseRepository<CampaignOptimizationResult, String> {

    Page<CampaignOptimizationResult> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable pageable);

    Page<CampaignOptimizationResult> findByPortfolioIdOrderByCreatedAtDesc(String portfolioId, Pageable pageable);

    Page<CampaignOptimizationResult> findByOptimizationTypeOrderByCreatedAtDesc(String optimizationType, Pageable pageable);

    Optional<CampaignOptimizationResult> findFirstByPortfolioIdOrderByCreatedAtDesc(String portfolioId);
}
