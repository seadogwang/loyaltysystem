package com.loyalty.platform.campaign.simulation.controller;

import com.loyalty.platform.campaign.simulation.dto.BaselineResult;
import com.loyalty.platform.campaign.simulation.dto.SimulationRequest;
import com.loyalty.platform.campaign.simulation.service.SimulationEngine;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignSimulationResult;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/campaign/simulation")
public class SimulationController {

    private final SimulationEngine simulationEngine;

    public SimulationController(SimulationEngine simulationEngine) {
        this.simulationEngine = simulationEngine;
    }

    /** 运行完整模拟 */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<CampaignSimulationResult>> runSimulation(
            @RequestBody SimulationRequest request) {
        CampaignSimulationResult result = simulationEngine.simulate(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 计算基线 */
    @PostMapping("/baseline")
    public ResponseEntity<ApiResponse<BaselineResult>> calculateBaseline(
            @RequestBody Map<String, String> body) {
        BaselineResult result = simulationEngine.calculateBaseline(
                body.get("goalId"), body.get("segmentCode"));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 获取模拟结果 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignSimulationResult>> getResult(@PathVariable String id) {
        CampaignSimulationResult result = simulationEngine.getResult(id)
                .orElseThrow(() -> new RuntimeException("Simulation not found: " + id));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 获取模拟历史 */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<CampaignSimulationResult>>> getHistory(
            @RequestParam String workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CampaignSimulationResult> results = simulationEngine.getHistory(workspaceId, page, size);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
