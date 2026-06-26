package com.loyalty.platform.campaign.planning.controller;

import com.loyalty.platform.campaign.planning.dto.BindPlanRequest;
import com.loyalty.platform.campaign.planning.dto.CreateInitiativeRequest;
import com.loyalty.platform.campaign.planning.dto.InitiativeContext;
import com.loyalty.platform.campaign.planning.service.InitiativeService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignInitiative;
import com.loyalty.platform.domain.entity.campaign.CampaignInitiativePlanRelation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 营销举措 REST API。
 */
@RestController
@RequestMapping("/api/campaign/initiative")
public class InitiativeController {

    private final InitiativeService initiativeService;

    public InitiativeController(InitiativeService initiativeService) {
        this.initiativeService = initiativeService;
    }

    /**
     * 创建举措。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignInitiative>> createInitiative(
            @RequestBody CreateInitiativeRequest request) {
        CampaignInitiative initiative = initiativeService.createInitiative(request);
        return ResponseEntity.ok(ApiResponse.success(initiative));
    }

    /**
     * 获取举措详情。
     */
    @GetMapping("/{initiativeId}")
    public ResponseEntity<ApiResponse<CampaignInitiative>> getInitiative(
            @PathVariable String initiativeId) {
        CampaignInitiative initiative = initiativeService.getInitiative(initiativeId);
        return ResponseEntity.ok(ApiResponse.success(initiative));
    }

    /**
     * 获取 Goal 下所有举措。
     */
    @GetMapping("/goal/{goalId}")
    public ResponseEntity<ApiResponse<List<CampaignInitiative>>> getByGoal(
            @PathVariable String goalId) {
        List<CampaignInitiative> initiatives = initiativeService.getInitiativesByGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(initiatives));
    }

    /**
     * 获取 Workspace 下所有举措。
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<ApiResponse<List<CampaignInitiative>>> getByWorkspace(
            @PathVariable String workspaceId) {
        List<CampaignInitiative> initiatives = initiativeService.getInitiativesByWorkspace(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(initiatives));
    }

    /**
     * 激活举措。
     */
    @PostMapping("/{initiativeId}/activate")
    public ResponseEntity<ApiResponse<CampaignInitiative>> activateInitiative(
            @PathVariable String initiativeId) {
        CampaignInitiative initiative = initiativeService.activateInitiative(initiativeId);
        return ResponseEntity.ok(ApiResponse.success(initiative));
    }

    /**
     * 暂停举措。
     */
    @PostMapping("/{initiativeId}/pause")
    public ResponseEntity<ApiResponse<CampaignInitiative>> pauseInitiative(
            @PathVariable String initiativeId) {
        CampaignInitiative initiative = initiativeService.pauseInitiative(initiativeId);
        return ResponseEntity.ok(ApiResponse.success(initiative));
    }

    /**
     * 完成举措。
     */
    @PostMapping("/{initiativeId}/complete")
    public ResponseEntity<ApiResponse<CampaignInitiative>> completeInitiative(
            @PathVariable String initiativeId) {
        CampaignInitiative initiative = initiativeService.completeInitiative(initiativeId);
        return ResponseEntity.ok(ApiResponse.success(initiative));
    }

    /**
     * 绑定 Plan 到举措。
     */
    @PostMapping("/{initiativeId}/bind-plan")
    public ResponseEntity<ApiResponse<CampaignInitiativePlanRelation>> bindPlan(
            @PathVariable String initiativeId,
            @RequestBody BindPlanRequest request) {
        CampaignInitiativePlanRelation relation = initiativeService.bindPlan(
                initiativeId, request.getPlanId(), request.getRole(), request.getWeight());
        return ResponseEntity.ok(ApiResponse.success(relation));
    }

    /**
     * 解绑 Plan。
     */
    @DeleteMapping("/{initiativeId}/plan/{planId}")
    public ResponseEntity<ApiResponse<Void>> unbindPlan(
            @PathVariable String initiativeId,
            @PathVariable String planId) {
        initiativeService.unbindPlan(initiativeId, planId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 获取举措上下文。
     */
    @GetMapping("/{initiativeId}/context")
    public ResponseEntity<ApiResponse<InitiativeContext>> loadContext(
            @PathVariable String initiativeId) {
        InitiativeContext context = initiativeService.loadContext(initiativeId);
        return ResponseEntity.ok(ApiResponse.success(context));
    }
}
