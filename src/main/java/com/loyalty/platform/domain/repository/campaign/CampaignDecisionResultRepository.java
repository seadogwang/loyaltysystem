package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignDecisionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignDecisionResultRepository extends CampaignBaseRepository<CampaignDecisionResult, String> {

    /** 查找 Portfolio 下最新的决策结果 */
    @Query("SELECT d FROM CampaignDecisionResult d WHERE d.portfolioId = :portfolioId ORDER BY d.createdAt DESC")
    Page<CampaignDecisionResult> findLatestByPortfolio(@Param("portfolioId") String portfolioId, Pageable pageable);

    /** 查找 Workspace 下所有决策结果（分页，最新在前） */
    Page<CampaignDecisionResult> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable pageable);

    /** 查找 Portfolio 下所有决策结果 */
    Page<CampaignDecisionResult> findByPortfolioIdOrderByCreatedAtDesc(String portfolioId, Pageable pageable);

    /** 根据状态查找 */
    Page<CampaignDecisionResult> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 查找特定 Portfolio 的最新一条决策（不限状态） */
    Optional<CampaignDecisionResult> findFirstByPortfolioIdOrderByCreatedAtDesc(String portfolioId);
}
