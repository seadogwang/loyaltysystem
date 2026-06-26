package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignCompileLog;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignCompileLogRepository extends BaseRepository<CampaignCompileLog, String> {

    List<CampaignCompileLog> findByPlanIdOrderByCreatedAtDesc(String planId);

    List<CampaignCompileLog> findByStatusOrderByCreatedAtDesc(String status);
}
