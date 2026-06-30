package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignArbitrationLog;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignArbitrationLogRepository extends CampaignBaseRepository<CampaignArbitrationLog, String> {

    /** 查找某个决策下的所有仲裁日志 */
    List<CampaignArbitrationLog> findByDecisionId(String decisionId);

    /** 查找某个决策下特定冲突类型的仲裁日志 */
    List<CampaignArbitrationLog> findByDecisionIdAndConflictType(String decisionId, String conflictType);
}
