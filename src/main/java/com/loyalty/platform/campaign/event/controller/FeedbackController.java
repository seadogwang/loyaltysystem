package com.loyalty.platform.campaign.event.controller;

import com.loyalty.platform.campaign.event.CampaignEventService;
import com.loyalty.platform.campaign.event.FeedbackLoopService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignFeedbackMetrics;
import com.loyalty.platform.domain.entity.campaign.CampaignModelDrift;
import com.loyalty.platform.domain.entity.campaign.CampaignStrategyAdjustment;
import com.loyalty.platform.domain.repository.campaign.CampaignFeedbackMetricsRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignModelDriftRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignStrategyAdjustmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/campaign/feedback")
public class FeedbackController {

    private final FeedbackLoopService feedbackLoopService;
    private final CampaignFeedbackMetricsRepository metricsRepository;
    private final CampaignModelDriftRepository driftRepository;
    private final CampaignStrategyAdjustmentRepository adjustmentRepository;
    private final CampaignEventService eventService;

    public FeedbackController(FeedbackLoopService feedbackLoopService,
                               CampaignFeedbackMetricsRepository metricsRepository,
                               CampaignModelDriftRepository driftRepository,
                               CampaignStrategyAdjustmentRepository adjustmentRepository,
                               CampaignEventService eventService) {
        this.feedbackLoopService = feedbackLoopService;
        this.metricsRepository = metricsRepository;
        this.driftRepository = driftRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.eventService = eventService;
    }

    /** 获取反馈指标 */
    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeedback(@PathVariable String planId) {
        Optional<CampaignFeedbackMetrics> metrics = feedbackLoopService.getLatestMetrics(planId);
        List<CampaignModelDrift> drifts = driftRepository.findByModelNameOrderByDetectedAtDesc("roi_prediction");
        List<CampaignStrategyAdjustment> adjustments = adjustmentRepository.findByPlanIdOrderByCreatedAtDesc(planId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "metrics", metrics.orElse(null), "drifts", drifts, "adjustments", adjustments)));
    }

    /** 触发反馈计算 */
    @PostMapping("/{planId}/calculate")
    public ResponseEntity<ApiResponse<CampaignFeedbackMetrics>> calculateFeedback(@PathVariable String planId) {
        CampaignFeedbackMetrics result = feedbackLoopService.calculateFeedback(planId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 获取漂移记录 */
    @GetMapping("/drift")
    public ResponseEntity<ApiResponse<List<CampaignModelDrift>>> getDrifts(
            @RequestParam(defaultValue = "roi_prediction") String modelName) {
        return ResponseEntity.ok(ApiResponse.success(driftRepository.findByModelNameOrderByDetectedAtDesc(modelName)));
    }

    /** 获取策略调整 */
    @GetMapping("/adjustments")
    public ResponseEntity<ApiResponse<List<CampaignStrategyAdjustment>>> getAdjustments(
            @RequestParam String workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(adjustmentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)));
    }

    /** 应用策略调整 */
    @PostMapping("/adjustments/{id}/apply")
    public ResponseEntity<ApiResponse<Map<String, String>>> applyAdjustment(@PathVariable String id) {
        Optional<CampaignStrategyAdjustment> adj = adjustmentRepository.findById(id);
        if (adj.isPresent()) {
            adj.get().setStatus("APPLIED");
            adj.get().setAppliedAt(java.time.Instant.now());
            adjustmentRepository.save(adj.get());
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "status", "APPLIED")));
    }

    /** 获取事件列表 */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<CampaignEventService.CampaignEvent>>> getEvents() {
        return ResponseEntity.ok(ApiResponse.success(eventService.getPendingEvents()));
    }
}
