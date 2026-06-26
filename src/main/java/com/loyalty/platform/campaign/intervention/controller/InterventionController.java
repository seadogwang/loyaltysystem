package com.loyalty.platform.campaign.intervention.controller;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignInterventionCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 人工干预 REST API。
 */
@RestController
@RequestMapping("/api/campaign/intervention")
public class InterventionController {

    private final InterventionService interventionService;

    public InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    /** 暂停活动 */
    @PostMapping("/{planId}/pause")
    public ResponseEntity<ApiResponse<CampaignInterventionCommand>> pause(
            @PathVariable String planId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                interventionService.pauseCampaign(planId, body.get("operatorId"), body.get("reason"))));
    }

    /** 恢复活动 */
    @PostMapping("/{planId}/resume")
    public ResponseEntity<ApiResponse<CampaignInterventionCommand>> resume(
            @PathVariable String planId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                interventionService.resumeCampaign(planId, body.get("operatorId"), body.get("reason"))));
    }

    /** 取消活动 */
    @PostMapping("/{planId}/cancel")
    public ResponseEntity<ApiResponse<CampaignInterventionCommand>> cancel(
            @PathVariable String planId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                interventionService.cancelCampaign(planId, body.get("operatorId"), body.get("reason"))));
    }

    /** 跳过节点 */
    @PostMapping("/{planId}/skip/{nodeId}")
    public ResponseEntity<ApiResponse<CampaignInterventionCommand>> skipNode(
            @PathVariable String planId, @PathVariable String nodeId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                interventionService.skipNode(planId, nodeId, body.get("operatorId"), body.get("reason"))));
    }

    /** 覆盖节点配置 */
    @PutMapping("/{planId}/config/{nodeId}")
    public ResponseEntity<ApiResponse<CampaignInterventionCommand>> overrideConfig(
            @PathVariable String planId, @PathVariable String nodeId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(
                interventionService.overrideNodeConfig(
                        planId, nodeId,
                        (Map<String, Object>) body.get("config"),
                        (String) body.get("operatorId"),
                        (String) body.get("reason"))));
    }

    /** 获取干预历史 */
    @GetMapping("/{planId}/interventions")
    public ResponseEntity<ApiResponse<List<CampaignInterventionCommand>>> getInterventions(
            @PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(interventionService.getInterventions(planId)));
    }

    /** 获取活动运行状态 */
    @GetMapping("/{planId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(
            @PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(interventionService.getPlanStatus(planId)));
    }

    /** 紧急限流 */
    @PostMapping("/throttle/{tenantId}")
    public ResponseEntity<ApiResponse<Void>> throttle(
            @PathVariable String tenantId, @RequestBody Map<String, Double> body) {
        interventionService.emergencyThrottle(tenantId, body.get("factor"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 取消限流 */
    @DeleteMapping("/throttle/{tenantId}")
    public ResponseEntity<ApiResponse<Void>> removeThrottle(@PathVariable String tenantId) {
        interventionService.removeThrottle(tenantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Worker 防护检查 */
    @PostMapping("/{planId}/check/{nodeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preCheck(
            @PathVariable String planId, @PathVariable String nodeId,
            @RequestParam(defaultValue = "default") String tenantId) {
        try {
            interventionService.checkBeforeExecution(planId, nodeId, tenantId);
            return ResponseEntity.ok(ApiResponse.success(Map.of("allowed", true, "message", "OK")));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("allowed", false, "message", e.getMessage())));
        }
    }
}
