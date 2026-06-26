package com.loyalty.platform.campaign.content.controller;

import com.loyalty.platform.campaign.content.service.ApprovalService;
import com.loyalty.platform.campaign.content.service.ContentService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignApprovalRecord;
import com.loyalty.platform.domain.entity.campaign.CampaignContentAsset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内容与合规治理 REST API。
 */
@RestController
@RequestMapping("/api/campaign/content")
public class ContentController {

    private final ContentService contentService;
    private final ApprovalService approvalService;

    public ContentController(ContentService contentService, ApprovalService approvalService) {
        this.contentService = contentService;
        this.approvalService = approvalService;
    }

    // ---- 素材 CRUD ----

    @PostMapping("/assets")
    public ResponseEntity<ApiResponse<CampaignContentAsset>> createAsset(@RequestBody CampaignContentAsset asset) {
        return ResponseEntity.ok(ApiResponse.success(contentService.createAsset(asset)));
    }

    @GetMapping("/assets/{assetId}")
    public ResponseEntity<ApiResponse<CampaignContentAsset>> getAsset(@PathVariable String assetId) {
        return ResponseEntity.ok(ApiResponse.success(contentService.getAsset(assetId)));
    }

    @PutMapping("/assets/{assetId}")
    public ResponseEntity<ApiResponse<CampaignContentAsset>> updateAsset(
            @PathVariable String assetId, @RequestBody CampaignContentAsset update) {
        return ResponseEntity.ok(ApiResponse.success(contentService.updateAsset(assetId, update)));
    }

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<List<CampaignContentAsset>>> listAssets(
            @RequestParam String programCode,
            @RequestParam(required = false) String type) {
        if (type != null) {
            return ResponseEntity.ok(ApiResponse.success(contentService.getAssetsByType(programCode, type)));
        }
        return ResponseEntity.ok(ApiResponse.success(contentService.getAssetsByProgram(programCode)));
    }

    @GetMapping("/assets/{assetId}/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preview(@PathVariable String assetId) {
        return ResponseEntity.ok(ApiResponse.success(contentService.preview(assetId)));
    }

    @PostMapping("/assets/{assetId}/render")
    public ResponseEntity<ApiResponse<String>> render(
            @PathVariable String assetId, @RequestBody Map<String, String> variables) {
        return ResponseEntity.ok(ApiResponse.success(contentService.renderTemplate(assetId, variables)));
    }

    // ---- 审批工作流 ----

    @PostMapping("/assets/{assetId}/submit")
    public ResponseEntity<ApiResponse<CampaignApprovalRecord>> submitAsset(
            @PathVariable String assetId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.submitAssetForApproval(assetId, body.get("requesterId"), body.get("comment"))));
    }

    @PostMapping("/assets/{assetId}/approve")
    public ResponseEntity<ApiResponse<CampaignApprovalRecord>> approveAsset(
            @PathVariable String assetId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.approveAsset(assetId, body.get("approverId"), body.get("comment"))));
    }

    @PostMapping("/assets/{assetId}/reject")
    public ResponseEntity<ApiResponse<CampaignApprovalRecord>> rejectAsset(
            @PathVariable String assetId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.rejectAsset(assetId, body.get("approverId"), body.get("reason"))));
    }

    @GetMapping("/assets/pending")
    public ResponseEntity<ApiResponse<List<CampaignContentAsset>>> pendingAssets(
            @RequestParam String programCode) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getPendingAssets(programCode)));
    }

    @GetMapping("/assets/{assetId}/history")
    public ResponseEntity<ApiResponse<List<CampaignApprovalRecord>>> approvalHistory(
            @PathVariable String assetId) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getAssetApprovalHistory(assetId)));
    }

    // ---- 计划审批 ----

    @PostMapping("/plans/{planId}/submit")
    public ResponseEntity<ApiResponse<CampaignApprovalRecord>> submitPlan(
            @PathVariable String planId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.submitPlanForApproval(planId, body.get("requesterId"))));
    }

    @PostMapping("/plans/{planId}/approve")
    public ResponseEntity<ApiResponse<CampaignApprovalRecord>> approvePlan(
            @PathVariable String planId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.approvePlan(planId, body.get("approverId"))));
    }

    // ---- 合规校验 ----

    @PostMapping("/assets/{assetId}/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateBeforeSend(
            @PathVariable String assetId) {
        try {
            approvalService.validateBeforeSend(assetId);
            return ResponseEntity.ok(ApiResponse.success(Map.of("valid", true, "message", "Content is approved")));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("valid", false, "message", e.getMessage())));
        }
    }
}
