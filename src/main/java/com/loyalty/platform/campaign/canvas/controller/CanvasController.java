package com.loyalty.platform.campaign.canvas.controller;

import com.loyalty.platform.campaign.canvas.dto.*;
import com.loyalty.platform.campaign.canvas.service.CanvasToBpmnCompiler;
import com.loyalty.platform.campaign.canvas.service.DagValidatorService;
import com.loyalty.platform.campaign.canvas.service.NodeRegistry;
import com.loyalty.platform.campaign.canvas.service.AiDagGeneratorService;
import com.loyalty.platform.campaign.planning.service.CampaignPlanService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignPlan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Canvas 编辑器 REST API。
 */
@RestController
@RequestMapping("/api/campaign/canvas")
public class CanvasController {

    private final CampaignPlanService planService;
    private final DagValidatorService dagValidator;
    private final CanvasToBpmnCompiler compiler;
    private final AiDagGeneratorService aiDagGenerator;
    private final NodeRegistry nodeRegistry;

    public CanvasController(CampaignPlanService planService,
                            DagValidatorService dagValidator,
                            CanvasToBpmnCompiler compiler,
                            AiDagGeneratorService aiDagGenerator,
                            NodeRegistry nodeRegistry) {
        this.planService = planService;
        this.dagValidator = dagValidator;
        this.compiler = compiler;
        this.aiDagGenerator = aiDagGenerator;
        this.nodeRegistry = nodeRegistry;
    }

    /** 创建一个新的空 Plan（含空 Canvas） */
    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<CampaignPlan>> createPlan(@RequestBody CampaignPlan plan) {
        CampaignPlan created = planService.createPlan(plan);
        // 初始化空的 Canvas DAG
        CanvasDag emptyDag = new CanvasDag(List.of(), List.of());
        planService.saveCanvas(created.getId(), emptyDag);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    /** 获取 Plan 详情 */
    @GetMapping("/plan/{planId}")
    public ResponseEntity<ApiResponse<CampaignPlan>> getPlan(@PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlan(planId)));
    }

    /** 获取 Plan 的 Canvas DAG */
    @GetMapping("/plan/{planId}/dag")
    public ResponseEntity<ApiResponse<CanvasDag>> getDag(@PathVariable String planId) {
        CanvasDag dag = planService.getCanvas(planId);
        return ResponseEntity.ok(ApiResponse.success(dag));
    }

    /** 保存 Canvas DAG */
    @PutMapping("/plan/{planId}/dag")
    public ResponseEntity<ApiResponse<CampaignPlan>> saveDag(@PathVariable String planId,
                                                              @RequestBody CanvasDag dag) {
        CampaignPlan plan = planService.saveCanvas(planId, dag);
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    /** 校验 DAG */
    @PostMapping("/plan/{planId}/validate")
    public ResponseEntity<ApiResponse<GraphValidationResult>> validate(@PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(planService.validateCanvas(planId)));
    }

    /** 校验任意 DAG（不保存） */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<GraphValidationResult>> validateDag(@RequestBody CanvasDag dag) {
        return ResponseEntity.ok(ApiResponse.success(dagValidator.validate(dag)));
    }

    /** 编译为 BPMN */
    @GetMapping("/plan/{planId}/compile")
    public ResponseEntity<ApiResponse<String>> compile(@PathVariable String planId) {
        String bpmnXml = planService.compileToBpmn(planId);
        return ResponseEntity.ok(ApiResponse.success(bpmnXml));
    }

    /** AI 生成 DAG */
    @PostMapping("/ai-generate")
    public ResponseEntity<ApiResponse<CanvasDag>> aiGenerate(@RequestBody AIRequest request) {
        CanvasDag dag = planService.generateDag(request);
        return ResponseEntity.ok(ApiResponse.success(dag));
    }

    /** 获取所有可用节点类型 */
    @GetMapping("/node-types")
    public ResponseEntity<ApiResponse<List<NodeRegistry.NodeTypeInfo>>> getNodeTypes() {
        return ResponseEntity.ok(ApiResponse.success(nodeRegistry.getAll()));
    }

    /** 更新 Plan 状态 */
    @PutMapping("/plan/{planId}/status")
    public ResponseEntity<ApiResponse<CampaignPlan>> updateStatus(@PathVariable String planId,
                                                                   @RequestBody String status) {
        return ResponseEntity.ok(ApiResponse.success(planService.updateStatus(planId, status)));
    }

    /** 提交审批 */
    @PostMapping("/plan/{planId}/submit")
    public ResponseEntity<ApiResponse<CampaignPlan>> submit(@PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(planService.submitForApproval(planId)));
    }

    /** 审批通过 */
    @PostMapping("/plan/{planId}/approve")
    public ResponseEntity<ApiResponse<CampaignPlan>> approve(@PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(planService.approve(planId, "system")));
    }
}
