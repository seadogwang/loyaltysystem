package com.loyalty.platform.campaign.simulation.controller;

import com.loyalty.platform.campaign.simulation.dto.OptimizationRequest;
import com.loyalty.platform.campaign.simulation.dto.OptimizationResultResponse;
import com.loyalty.platform.campaign.simulation.service.OptimizationEngine;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignOptimizationResult;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campaign/optimization")
public class OptimizationController {

    private final OptimizationEngine optimizationEngine;

    public OptimizationController(OptimizationEngine optimizationEngine) {
        this.optimizationEngine = optimizationEngine;
    }

    /** 运行优化 */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<OptimizationResultResponse>> runOptimization(
            @RequestBody OptimizationRequest request) {
        OptimizationResultResponse result;
        if ("GENETIC".equalsIgnoreCase(request.getOptimizationType())) {
            result = optimizationEngine.optimizeGenetic(request);
        } else {
            result = optimizationEngine.optimizeGreedy(request);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 获取优化结果 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignOptimizationResult>> getResult(@PathVariable String id) {
        CampaignOptimizationResult result = optimizationEngine.getResult(id)
                .orElseThrow(() -> new RuntimeException("Optimization not found: " + id));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 获取优化历史 */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<CampaignOptimizationResult>>> getHistory(
            @RequestParam String portfolioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CampaignOptimizationResult> results = optimizationEngine.getHistory(portfolioId, page, size);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
