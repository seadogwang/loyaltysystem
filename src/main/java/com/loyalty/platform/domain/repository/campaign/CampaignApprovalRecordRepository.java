package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignApprovalRecord;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignApprovalRecordRepository extends CampaignBaseRepository<CampaignApprovalRecord, String> {

    List<CampaignApprovalRecord> findByAssetId(String assetId);

    List<CampaignApprovalRecord> findByPlanId(String planId);

    List<CampaignApprovalRecord> findByApproverId(String approverId);

    List<CampaignApprovalRecord> findByAction(String action);
}
