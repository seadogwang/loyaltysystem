package com.loyalty.platform.campaign.decision.controller;

import com.loyalty.platform.campaign.decision.dto.*;
import com.loyalty.platform.campaign.decision.service.AttentionBudgetService;
import com.loyalty.platform.campaign.decision.service.DecisionEngineService;
import com.loyalty.platform.campaign.decision.service.DecisionRollbackService;
import com.loyalty.platform.campaign.decision.service.SimulationEngine;
import com.loyalty.platform.common.dto.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 营销决策引擎 REST API。
 *
 * <p>端点分组：
 * <ul>
 *   <li>完整决策流程：execute / apply / rollback</li>
 *   <li>决策查询：latest / detail / history</li>
 *   <li>预算分配（v1 兼容）：allocate / allocate/constrained</li>
 *   <li>模拟预测：simulate / simulate/batch / simulate/compare</li>
 *   <li>注意力预算：attention/*</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/campaign/decision")
public class DecisionController {

    private final DecisionEngineService decisionEngine;
    private final SimulationEngine simulationEngine;
    private final AttentionBudgetService attentionBudgetService;
    private final DecisionRollbackService rollbackService;

    public DecisionController(DecisionEngineService decisionEngine,
                              SimulationEngine simulationEngine,
                              AttentionBudgetService attentionBudgetService,
                              DecisionRollbackService rollbackService) {
        this.decisionEngine = decisionEngine;
        this.simulationEngine = simulationEngine;
        this.attentionBudgetService = attentionBudgetService;
        this.rollbackService = rollbackService;
    }

    // ========================================================================
    // 完整决策流程
    // ========================================================================

    /**
     * 执行完整决策 — 从机会加载到结果持久化。
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<DecisionResultResponse>> executeDecision(
            @RequestBody DecisionRequest request) {
        DecisionResultResponse result = decisionEngine.executeFullDecision(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 应用决策 — 将 DRAFT 决策设为 APPLIED 并触发执行。
     */
    @PostMapping("/{decisionId}/apply")
    public ResponseEntity<ApiResponse<DecisionResultResponse>> applyDecision(
            @PathVariable String decisionId) {
        DecisionResultResponse result = decisionEngine.applyDecision(decisionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 回滚决策 — 将已应用的决策回滚。
     */
    @PostMapping("/{decisionId}/rollback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollbackDecision(
            @PathVariable String decisionId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Manual rollback") : "Manual rollback";
        rollbackService.rollback(decisionId, reason);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "decisionId", decisionId,
                "status", "ROLLED_BACK",
                "reason", reason
        )));
    }

    // ========================================================================
    // 决策查询
    // ========================================================================

    /**
     * 获取 Portfolio 的最新决策。
     */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<DecisionResultResponse>> getLatestDecision(
            @RequestParam String portfolioId) {
        DecisionResultResponse result = decisionEngine.getLatestDecision(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取决策详情。
     */
    @GetMapping("/{decisionId}")
    public ResponseEntity<ApiResponse<DecisionResultResponse>> getDecision(
            @PathVariable String decisionId) {
        DecisionResultResponse result = decisionEngine.getDecision(decisionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取历史决策列表（按 Workspace，分页）。
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<DecisionSummary>>> getDecisionHistory(
            @RequestParam String workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DecisionSummary> result = decisionEngine.getDecisionHistory(workspaceId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ========================================================================
    // 预算分配（v1 兼容）
    // ========================================================================

    /**
     * 预算分配（贪心 + ROI 排序）。
     */
    @PostMapping("/allocate")
    public ResponseEntity<ApiResponse<AllocationResult>> allocateBudget(
            @RequestBody AllocationRequest request) {
        AllocationResult result = decisionEngine.allocateBudget(
                request.getCandidates(), request.getTotalBudget());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 带约束的预算分配（考虑渠道容量）。
     */
    @PostMapping("/allocate/constrained")
    public ResponseEntity<ApiResponse<AllocationResult>> allocateWithConstraints(
            @RequestBody ConstrainedAllocationRequest request) {
        AllocationResult result = decisionEngine.allocateWithConstraints(
                request.getCandidates(), request.getTotalBudget(), request.getChannelCapacity());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 冲突仲裁 — 计算优先级分数。
     */
    @PostMapping("/prioritize")
    public ResponseEntity<ApiResponse<List<CampaignCandidate>>> prioritize(
            @RequestBody List<CampaignCandidate> candidates) {
        List<CampaignCandidate> prioritized = decisionEngine.prioritize(candidates);
        return ResponseEntity.ok(ApiResponse.success(prioritized));
    }

    // ========================================================================
    // 模拟预测
    // ========================================================================

    /**
     * 单活动模拟预测。
     */
    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<SimulationResult>> simulate(
            @RequestBody SimulationRequest request) {
        SimulationResult result = simulationEngine.simulate(
                request.getCandidate(), request.getAudienceSize());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 批量模拟预测。
     */
    @PostMapping("/simulate/batch")
    public ResponseEntity<ApiResponse<List<SimulationResult>>> simulateBatch(
            @RequestBody BatchSimulationRequest request) {
        List<SimulationResult> results = simulationEngine.simulateBatch(
                request.getCandidates(), request.getAudienceSize());
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * What-if 对比模拟。
     */
    @PostMapping("/simulate/compare")
    public ResponseEntity<ApiResponse<SimulationCompareResult>> comparePlans(
            @RequestBody CompareRequest request) {
        SimulationCompareResult result = simulationEngine.comparePlans(
                request.getPlanAId(), request.getPlanAName(), request.getCandidatesA(),
                request.getPlanBId(), request.getPlanBName(), request.getCandidatesB(),
                request.getAudienceSize());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ========================================================================
    // 注意力预算
    // ========================================================================

    /**
     * 检查用户是否有曝光配额。
     */
    @GetMapping("/attention/{userId}/{channel}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAttentionBudget(
            @PathVariable String userId,
            @PathVariable String channel) {
        boolean hasQuota = attentionBudgetService.hasExposureQuota(userId, channel);
        int remaining = attentionBudgetService.getRemainingQuota(userId, channel);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "userId", userId,
                "channel", channel,
                "hasQuota", hasQuota,
                "remaining", remaining
        )));
    }

    /**
     * 记录曝光消耗。
     */
    @PostMapping("/attention/{userId}/{channel}/expose")
    public ResponseEntity<ApiResponse<Void>> recordExposure(
            @PathVariable String userId,
            @PathVariable String channel) {
        attentionBudgetService.recordExposure(userId, channel);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 设置用户每日最大曝光次数。
     */
    @PutMapping("/attention/{userId}/{channel}/limit")
    public ResponseEntity<ApiResponse<Void>> setExposureLimit(
            @PathVariable String userId,
            @PathVariable String channel,
            @RequestBody Map<String, Integer> body) {
        attentionBudgetService.setMaxExposure(userId, channel, body.get("maxExposure"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ========================================================================
    // Request DTOs（内部静态类）
    // ========================================================================

    @lombok.Data
    public static class AllocationRequest {
        private List<CampaignCandidate> candidates;
        private BigDecimal totalBudget;
    }

    @lombok.Data
    public static class ConstrainedAllocationRequest {
        private List<CampaignCandidate> candidates;
        private BigDecimal totalBudget;
        private Map<String, Integer> channelCapacity;
    }

    @lombok.Data
    public static class SimulationRequest {
        private CampaignCandidate candidate;
        private long audienceSize;
    }

    @lombok.Data
    public static class BatchSimulationRequest {
        private List<CampaignCandidate> candidates;
        private long audienceSize;
    }

    @lombok.Data
    public static class CompareRequest {
        private String planAId;
        private String planAName;
        private List<CampaignCandidate> candidatesA;
        private String planBId;
        private String planBName;
        private List<CampaignCandidate> candidatesB;
        private long audienceSize;
    }
}
