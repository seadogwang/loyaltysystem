package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignModelDrift;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignModelDriftRepository extends BaseRepository<CampaignModelDrift, String> {

    List<CampaignModelDrift> findByModelNameOrderByDetectedAtDesc(String modelName);

    List<CampaignModelDrift> findByDriftDetectedTrueOrderByDetectedAtDesc();

    List<CampaignModelDrift> findByStatusOrderByDetectedAtDesc(String status);
}
