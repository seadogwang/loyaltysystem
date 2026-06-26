package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignWorkspace;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignWorkspaceRepository extends BaseRepository<CampaignWorkspace, String> {

    List<CampaignWorkspace> findByProgramCode(String programCode);

    List<CampaignWorkspace> findByStatus(String status);

    List<CampaignWorkspace> findByCreatedBy(String createdBy);

    @Query("SELECT w FROM CampaignWorkspace w WHERE w.programCode = :pc AND w.status = 'ACTIVE'")
    List<CampaignWorkspace> findActiveByProgramCode(@Param("pc") String programCode);

    @Override
    @Query("SELECT w FROM CampaignWorkspace w WHERE w.id = :id")
    Optional<CampaignWorkspace> findByIdWithTenant(@Param("id") String id);

    @Override
    @Query("SELECT w FROM CampaignWorkspace w WHERE w.programCode = :pc")
    List<CampaignWorkspace> findAllByProgramCode(@Param("pc") String programCode);
}
