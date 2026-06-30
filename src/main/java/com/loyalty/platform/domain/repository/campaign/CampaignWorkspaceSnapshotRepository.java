package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignWorkspaceSnapshot;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignWorkspaceSnapshotRepository extends CampaignBaseRepository<CampaignWorkspaceSnapshot, String> {

    List<CampaignWorkspaceSnapshot> findByWorkspaceId(String workspaceId);

    List<CampaignWorkspaceSnapshot> findByWorkspaceIdAndSnapshotType(String workspaceId, String snapshotType);

    @Query("SELECT COALESCE(MAX(s.version), 0) + 1 FROM CampaignWorkspaceSnapshot s WHERE s.workspaceId = :wsId AND s.snapshotType = :type")
    int getNextVersion(@Param("wsId") String workspaceId, @Param("type") String snapshotType);
}
