package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignNodeDefinition;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignNodeDefinitionRepository extends BaseRepository<CampaignNodeDefinition, String> {

    List<CampaignNodeDefinition> findByStatusOrderByCategoryAsc(String status);

    List<CampaignNodeDefinition> findByCategoryAndStatus(String category, String status);

    @Query("SELECT d FROM CampaignNodeDefinition d WHERE d.status = 'ACTIVE' ORDER BY d.category, d.name")
    List<CampaignNodeDefinition> findAllActive();
}
