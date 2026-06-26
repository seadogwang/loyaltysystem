package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignOpportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignOpportunityRepository extends BaseRepository<CampaignOpportunity, String> {

    List<CampaignOpportunity> findByWorkspaceIdAndGoalId(String workspaceId, String goalId);

    Page<CampaignOpportunity> findByWorkspaceIdAndGoalId(String workspaceId, String goalId, Pageable pageable);

    List<CampaignOpportunity> findByMemberId(String memberId);

    List<CampaignOpportunity> findByOpportunityType(String opportunityType);

    List<CampaignOpportunity> findByStatus(String status);

    List<CampaignOpportunity> findByGoalIdAndStatus(String goalId, String status);

    @Query("SELECT o FROM CampaignOpportunity o WHERE o.goalId IN :goalIds AND o.status = :status")
    List<CampaignOpportunity> findByGoalIdInAndStatus(@Param("goalIds") List<String> goalIds,
                                                       @Param("status") String status);

    List<CampaignOpportunity> findByStatusAndScoreGreaterThanEqual(String status, java.math.BigDecimal minScore);

    @Query("SELECT o FROM CampaignOpportunity o WHERE o.workspaceId = :wsId AND o.goalId = :goalId " +
           "AND (:types IS NULL OR o.opportunityType IN :types) " +
           "AND (:minScore IS NULL OR o.score >= :minScore) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "ORDER BY o.score DESC")
    List<CampaignOpportunity> findByFilters(@Param("wsId") String workspaceId,
                                             @Param("goalId") String goalId,
                                             @Param("types") List<String> types,
                                             @Param("minScore") java.math.BigDecimal minScore,
                                             @Param("status") String status,
                                             Pageable pageable);

    @Query("SELECT COUNT(o) FROM CampaignOpportunity o WHERE o.workspaceId = :wsId AND o.goalId = :goalId")
    long countByWorkspaceAndGoal(@Param("wsId") String workspaceId, @Param("goalId") String goalId);

    @Query("SELECT COUNT(o) FROM CampaignOpportunity o WHERE o.workspaceId = :wsId " +
           "AND o.goalId = :goalId AND o.score >= :minScore")
    long countHighValue(@Param("wsId") String workspaceId, @Param("goalId") String goalId,
                         @Param("minScore") java.math.BigDecimal minScore);

    @Query("SELECT o.opportunityType, COUNT(o) FROM CampaignOpportunity o " +
           "WHERE o.workspaceId = :wsId AND o.goalId = :goalId " +
           "GROUP BY o.opportunityType")
    List<Object[]> countByType(@Param("wsId") String workspaceId, @Param("goalId") String goalId);

    @Query("SELECT AVG(o.score) FROM CampaignOpportunity o " +
           "WHERE o.workspaceId = :wsId AND o.goalId = :goalId")
    Double avgScore(@Param("wsId") String workspaceId, @Param("goalId") String goalId);

    @Modifying
    @Query("UPDATE CampaignOpportunity o SET o.status = 'EXPIRED' WHERE o.expiresAt < :now AND o.status = 'ACTIVE'")
    int expireOpportunities(@Param("now") Instant now);
}
