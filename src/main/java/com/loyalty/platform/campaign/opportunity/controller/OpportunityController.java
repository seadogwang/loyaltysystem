package com.loyalty.platform.campaign.opportunity.controller;

import com.loyalty.platform.campaign.ai.skill.SkillExecutionContext;
import com.loyalty.platform.campaign.opportunity.dto.DiscoverOpportunitiesRequest;
import com.loyalty.platform.campaign.opportunity.dto.DiscoverOpportunitiesResponse;
import com.loyalty.platform.campaign.opportunity.dto.Opportunity;
import com.loyalty.platform.campaign.opportunity.service.ExternalSignalService;
import com.loyalty.platform.campaign.opportunity.service.OpportunityService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.ExternalSignal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 机会智能 REST API — 符合第2.5节前后端 JSON 交互规范。
 */
@RestController
@RequestMapping("/api/campaign/opportunity")
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final ExternalSignalService externalSignalService;

    public OpportunityController(OpportunityService opportunityService,
                                  ExternalSignalService externalSignalService) {
        this.opportunityService = opportunityService;
        this.externalSignalService = externalSignalService;
    }

    // ==================== 机会发现 ====================

    /**
     * 发现机会（长耗时操作）。
     *
     * POST /api/campaign/opportunity/discover
     */
    @PostMapping("/discover")
    public ResponseEntity<ApiResponse<DiscoverOpportunitiesResponse>> discover(
            @RequestBody DiscoverOpportunitiesRequest request) {
        DiscoverOpportunitiesResponse response = opportunityService.discoverOpportunities(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 查询机会列表（含筛选和分页）。
     *
     * GET /api/campaign/opportunity?workspaceId=ws_001&goalId=goal_001
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Opportunity>>> listOpportunities(
            @RequestParam String workspaceId,
            @RequestParam String goalId,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<Opportunity> opportunities = opportunityService.findOpportunities(
                workspaceId, goalId, types, minScore, status, limit, offset);
        return ResponseEntity.ok(ApiResponse.success(opportunities));
    }

    /**
     * 消费机会。
     *
     * POST /api/campaign/opportunity/{opportunityId}/consume
     */
    @PostMapping("/{opportunityId}/consume")
    public ResponseEntity<ApiResponse<Opportunity>> consume(
            @PathVariable String opportunityId) {
        Opportunity opp = opportunityService.consumeOpportunity(opportunityId);
        return ResponseEntity.ok(ApiResponse.success(opp));
    }

    // ==================== 外部信号 ====================

    /**
     * 获取外部信号列表。
     *
     * GET /api/campaign/external-signal?programCode=BRAND_A&severity=CRITICAL
     */
    @GetMapping("/external-signal")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExternalSignals(
            @RequestParam String programCode,
            @RequestParam(required = false) String severity) {
        List<ExternalSignal> signals;
        if (severity != null) {
            signals = externalSignalService.getSignalsBySeverity(programCode, severity);
        } else {
            signals = externalSignalService.getActiveSignals(programCode);
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("signals", signals, "total", signals.size())));
    }

    /**
     * 手动触发技能执行。
     *
     * POST /api/campaign/external-signal/execute
     */
    @PostMapping("/external-signal/execute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeSkill(
            @RequestBody Map<String, Object> body) {
        String skillName = (String) body.get("skillName");
        @SuppressWarnings("unchecked")
        Map<String, Object> contextMap = (Map<String, Object>) body.get("context");

        SkillExecutionContext context = SkillExecutionContext.builder()
                .programCode((String) body.getOrDefault("programCode", "BRAND_A"))
                .competitorUrls(contextMap != null
                        ? (List<String>) contextMap.get("competitorUrls") : null)
                .keywords(contextMap != null
                        ? (List<String>) contextMap.get("keywords") : null)
                .build();

        long start = System.currentTimeMillis();
        List<ExternalSignal> signals = externalSignalService.executeSkillByName(skillName, context);
        long elapsed = System.currentTimeMillis() - start;

        List<String> signalIds = signals.stream().map(ExternalSignal::getId).toList();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "skillName", skillName,
                "signalsGenerated", signals.size(),
                "signalIds", signalIds,
                "executionTimeMs", elapsed
        )));
    }

    /**
     * Webhook 入口 — 创建外部信号。
     */
    @PostMapping("/external-signal")
    public ResponseEntity<ApiResponse<ExternalSignal>> createExternalSignal(
            @RequestBody ExternalSignal signal) {
        ExternalSignal created = externalSignalService.createSignal(signal);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    /**
     * 获取外部信号影响系数。
     */
    @PostMapping("/external-signal/weight")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateWeight(
            @RequestBody Map<String, String> body) {
        List<ExternalSignal> signals = externalSignalService.getActiveSignals(body.get("programCode"));
        double weight = externalSignalService.calculateExternalWeight(signals, body.get("segmentCode"));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "weight", weight,
                "signalCount", signals.size()
        )));
    }
}
