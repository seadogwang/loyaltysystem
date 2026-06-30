package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignGoal;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignGoalRepository extends CampaignBaseRepository<CampaignGoal, String> {

    List<CampaignGoal> findByWorkspaceId(String workspaceId);

    Optional<CampaignGoal> findByWorkspaceIdAndStatus(String workspaceId, String status);

    @Query("SELECT g FROM CampaignGoal g WHERE g.workspaceId = :wsId AND g.status = 'ACTIVE'")
    Optional<CampaignGoal> findActiveGoal(@Param("wsId") String workspaceId);

    /** 停用 Workspace 下所有 ACTIVE Goal */
    @Modifying
    @Query("UPDATE CampaignGoal g SET g.status = 'PAUSED' WHERE g.workspaceId = :wsId AND g.status = 'ACTIVE'")
    int deactivateAllByWorkspace(@Param("wsId") String workspaceId);
}
