package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignWorkspaceMember;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignWorkspaceMemberRepository extends BaseRepository<CampaignWorkspaceMember, String> {

    List<CampaignWorkspaceMember> findByWorkspaceId(String workspaceId);

    boolean existsByWorkspaceIdAndUserId(String workspaceId, String userId);

    void deleteByWorkspaceIdAndUserId(String workspaceId, String userId);
}
