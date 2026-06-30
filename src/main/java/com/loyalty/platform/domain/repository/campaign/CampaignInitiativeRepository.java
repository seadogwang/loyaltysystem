package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignInitiative;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignInitiativeRepository extends CampaignBaseRepository<CampaignInitiative, String> {

    List<CampaignInitiative> findByWorkspaceId(String workspaceId);

    List<CampaignInitiative> findByGoalId(String goalId);

    List<CampaignInitiative> findByWorkspaceIdAndStatus(String workspaceId, String status);

    @Query("SELECT i FROM CampaignInitiative i WHERE i.workspaceId = :wsId AND i.status = 'ACTIVE'")
    List<CampaignInitiative> findActiveByWorkspaceId(@Param("wsId") String workspaceId);

    @Query("SELECT i FROM CampaignInitiative i WHERE i.goalId IN :goalIds")
    List<CampaignInitiative> findByGoalIds(@Param("goalIds") List<String> goalIds);
}
