package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignInterventionCommand;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignInterventionCommandRepository extends BaseRepository<CampaignInterventionCommand, String> {

    List<CampaignInterventionCommand> findByPlanIdOrderByCreatedAtDesc(String planId);

    List<CampaignInterventionCommand> findByCommandType(String commandType);

    List<CampaignInterventionCommand> findByOperatorId(String operatorId);
}
