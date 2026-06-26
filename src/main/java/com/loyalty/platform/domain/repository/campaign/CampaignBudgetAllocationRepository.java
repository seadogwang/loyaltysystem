package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignBudgetAllocation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignBudgetAllocationRepository extends BaseRepository<CampaignBudgetAllocation, String> {

    /** 查找某个决策下的所有分配明细 */
    List<CampaignBudgetAllocation> findByDecisionId(String decisionId);

    /** 查找某个 Initiative 的所有分配记录 */
    List<CampaignBudgetAllocation> findByInitiativeId(String initiativeId);

    /** 查找某个决策下特定状态的分配 */
    List<CampaignBudgetAllocation> findByDecisionIdAndStatus(String decisionId, String status);
}
