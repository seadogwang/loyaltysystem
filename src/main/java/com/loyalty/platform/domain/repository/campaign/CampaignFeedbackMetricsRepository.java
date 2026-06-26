package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignFeedbackMetrics;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignFeedbackMetricsRepository extends BaseRepository<CampaignFeedbackMetrics, String> {

    Optional<CampaignFeedbackMetrics> findFirstByPlanIdOrderByCalculatedAtDesc(String planId);

    java.util.List<CampaignFeedbackMetrics> findByPlanIdOrderByCalculatedAtDesc(String planId);
}
