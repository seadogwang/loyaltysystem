package com.loyalty.platform.campaign.planning.controller;

import com.loyalty.platform.campaign.planning.dto.CreateGoalRequest;
import com.loyalty.platform.campaign.planning.dto.GoalContext;
import com.loyalty.platform.campaign.planning.service.GoalService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignGoal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 营销目标 REST API。
 */
@RestController
@RequestMapping("/api/campaign/goal")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * 创建目标。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignGoal>> createGoal(
            @RequestBody CreateGoalRequest request) {
        CampaignGoal goal = goalService.createGoal(request);
        return ResponseEntity.ok(ApiResponse.success(goal));
    }

    /**
     * 获取目标详情。
     */
    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<CampaignGoal>> getGoal(
            @PathVariable String goalId) {
        CampaignGoal goal = goalService.getGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(goal));
    }

    /**
     * 获取 Workspace 下所有目标。
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<ApiResponse<List<CampaignGoal>>> getGoalsByWorkspace(
            @PathVariable String workspaceId) {
        List<CampaignGoal> goals = goalService.getGoalsByWorkspace(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(goals));
    }

    /**
     * 激活目标。
     */
    @PostMapping("/{goalId}/activate")
    public ResponseEntity<ApiResponse<CampaignGoal>> activateGoal(
            @PathVariable String goalId) {
        CampaignGoal goal = goalService.activateGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(goal));
    }

    /**
     * 暂停目标。
     */
    @PostMapping("/{goalId}/pause")
    public ResponseEntity<ApiResponse<CampaignGoal>> pauseGoal(
            @PathVariable String goalId) {
        CampaignGoal goal = goalService.pauseGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(goal));
    }

    /**
     * 完成目标。
     */
    @PostMapping("/{goalId}/complete")
    public ResponseEntity<ApiResponse<CampaignGoal>> completeGoal(
            @PathVariable String goalId) {
        CampaignGoal goal = goalService.completeGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(goal));
    }

    /**
     * 归档目标。
     */
    @PostMapping("/{goalId}/archive")
    public ResponseEntity<ApiResponse<CampaignGoal>> archiveGoal(
            @PathVariable String goalId) {
        CampaignGoal goal = goalService.archiveGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(goal));
    }

    /**
     * 获取目标上下文（含 KPI 和进度）。
     */
    @GetMapping("/{goalId}/context")
    public ResponseEntity<ApiResponse<GoalContext>> loadContext(
            @PathVariable String goalId) {
        GoalContext context = goalService.loadContext(goalId);
        return ResponseEntity.ok(ApiResponse.success(context));
    }

    /**
     * 计算目标进度。
     */
    @GetMapping("/{goalId}/progress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProgress(
            @PathVariable String goalId) {
        Double progress = goalService.calculateProgress(goalId);
        CampaignGoal goal = goalService.getGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "goalId", goalId,
                "progress", progress,
                "targetValue", goal.getTargetValue(),
                "currentValue", goal.getCurrentValue()
        )));
    }

    /**
     * 更新 KPI 值。
     */
    @PutMapping("/{goalId}/kpi/{kpiType}")
    public ResponseEntity<ApiResponse<Void>> updateKpiValue(
            @PathVariable String goalId,
            @PathVariable String kpiType,
            @RequestBody Map<String, BigDecimal> body) {
        goalService.updateKpiValue(goalId, kpiType, body.get("value"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
