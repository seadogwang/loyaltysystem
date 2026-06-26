package com.loyalty.platform.campaign.content.service;

import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.common.exception.ResourceNotFoundException;
import com.loyalty.platform.domain.entity.campaign.CampaignApprovalRecord;
import com.loyalty.platform.domain.entity.campaign.CampaignContentAsset;
import com.loyalty.platform.domain.entity.campaign.CampaignPlan;
import com.loyalty.platform.domain.repository.campaign.CampaignApprovalRecordRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignContentAssetRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审批服务 — 素材和计划的审批工作流。
 */
@Service
@Transactional
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final CampaignApprovalRecordRepository approvalRepo;
    private final CampaignContentAssetRepository assetRepo;
    private final CampaignPlanRepository planRepo;

    public ApprovalService(CampaignApprovalRecordRepository approvalRepo,
                           CampaignContentAssetRepository assetRepo,
                           CampaignPlanRepository planRepo) {
        this.approvalRepo = approvalRepo;
        this.assetRepo = assetRepo;
        this.planRepo = planRepo;
    }

    /** 提交素材审批 */
    public CampaignApprovalRecord submitAssetForApproval(String assetId, String requesterId, String comment) {
        CampaignContentAsset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));

        if (!"DRAFT".equals(asset.getStatus())) {
            throw new BusinessException("ERR_ASSET_ALREADY_SUBMITTED", "Only DRAFT asset can be submitted for approval");
        }

        asset.setStatus("PENDING_APPROVAL");
        assetRepo.save(asset);

        CampaignApprovalRecord record = CampaignApprovalRecord.builder()
                .id(UUID.randomUUID().toString())
                .assetId(assetId)
                .requesterId(requesterId)
                .action("SUBMITTED")
                .comment(comment)
                .snapshotBefore("{\"status\":\"DRAFT\"}")
                .build();
        record = approvalRepo.save(record);

        log.info("Asset submitted for approval: assetId={}, requester={}", assetId, requesterId);
        return record;
    }

    /** 审批通过素材 */
    public CampaignApprovalRecord approveAsset(String assetId, String approverId, String comment) {
        CampaignContentAsset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));

        if (!"PENDING_APPROVAL".equals(asset.getStatus())) {
            throw new BusinessException("ERR_ASSET_NOT_PENDING", "Asset is not pending approval");
        }

        asset.setStatus("APPROVED");
        asset.setApprovedBy(approverId);
        asset.setApprovedAt(LocalDateTime.now());
        assetRepo.save(asset);

        CampaignApprovalRecord record = CampaignApprovalRecord.builder()
                .id(UUID.randomUUID().toString())
                .assetId(assetId)
                .approverId(approverId)
                .action("APPROVED")
                .comment(comment)
                .build();
        record = approvalRepo.save(record);

        log.info("Asset approved: assetId={}, approver={}", assetId, approverId);
        return record;
    }

    /** 驳回素材 */
    public CampaignApprovalRecord rejectAsset(String assetId, String approverId, String reason) {
        CampaignContentAsset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));

        if (!"PENDING_APPROVAL".equals(asset.getStatus())) {
            throw new BusinessException("ERR_ASSET_NOT_PENDING", "Asset is not pending approval");
        }

        asset.setStatus("REJECTED");
        assetRepo.save(asset);

        CampaignApprovalRecord record = CampaignApprovalRecord.builder()
                .id(UUID.randomUUID().toString())
                .assetId(assetId)
                .approverId(approverId)
                .action("REJECTED")
                .comment(reason)
                .build();
        record = approvalRepo.save(record);

        log.info("Asset rejected: assetId={}, approver={}, reason={}", assetId, approverId, reason);
        return record;
    }

    /** 提交计划审批 */
    public CampaignApprovalRecord submitPlanForApproval(String planId, String requesterId) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        plan.setStatus("GENERATED");
        planRepo.save(plan);

        CampaignApprovalRecord record = CampaignApprovalRecord.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .requesterId(requesterId)
                .action("SUBMITTED")
                .build();
        record = approvalRepo.save(record);

        log.info("Plan submitted for approval: planId={}", planId);
        return record;
    }

    /** 审批通过计划 */
    public CampaignApprovalRecord approvePlan(String planId, String approverId) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        plan.setStatus("APPROVED");
        plan.setApprovedBy(approverId);
        plan.setApprovedAt(LocalDateTime.now());
        planRepo.save(plan);

        CampaignApprovalRecord record = CampaignApprovalRecord.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .approverId(approverId)
                .action("APPROVED")
                .build();
        record = approvalRepo.save(record);

        log.info("Plan approved: planId={}, approver={}", planId, approverId);
        return record;
    }

    /** 发送前校验（内容必须已审批） */
    public void validateBeforeSend(String assetId) {
        CampaignContentAsset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));
        if (!"APPROVED".equals(asset.getStatus())) {
            throw new BusinessException("ERR_CONTENT_NOT_APPROVED",
                    "Content not approved: " + assetId + " (status=" + asset.getStatus() + ")");
        }
    }

    /** 获取素材的审批历史 */
    @Transactional(readOnly = true)
    public List<CampaignApprovalRecord> getAssetApprovalHistory(String assetId) {
        return approvalRepo.findByAssetId(assetId);
    }

    /** 获取计划的审批历史 */
    @Transactional(readOnly = true)
    public List<CampaignApprovalRecord> getPlanApprovalHistory(String planId) {
        return approvalRepo.findByPlanId(planId);
    }

    /** 获取待审批列表 */
    @Transactional(readOnly = true)
    public List<CampaignContentAsset> getPendingAssets(String programCode) {
        return assetRepo.findByProgramCodeAndStatus(programCode, "PENDING_APPROVAL");
    }
}
