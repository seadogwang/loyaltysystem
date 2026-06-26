package com.loyalty.platform.campaign.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.campaign.canvas.dto.CanvasDag;
import com.loyalty.platform.campaign.canvas.service.CanvasToBpmnCompiler;
import com.loyalty.platform.common.exception.ResourceNotFoundException;
import com.loyalty.platform.domain.entity.campaign.CampaignPlan;
import com.loyalty.platform.domain.repository.campaign.CampaignPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Zeebe 流程部署服务 — 将 Canvas DAG 编译后部署到 Zeebe。
 *
 * <p>开发阶段：模拟部署（仅记录到数据库）。
 * 生产阶段：通过 ZeebeClient 实际部署。
 */
@Service
@Transactional
public class ZeebeDeployService {

    private static final Logger log = LoggerFactory.getLogger(ZeebeDeployService.class);

    private final CampaignPlanRepository planRepo;
    private final CanvasToBpmnCompiler compiler;
    private final ObjectMapper objectMapper;

    /** 模拟部署计数器 */
    private int deployCounter = 0;

    public ZeebeDeployService(CampaignPlanRepository planRepo,
                               CanvasToBpmnCompiler compiler,
                               ObjectMapper objectMapper) {
        this.planRepo = planRepo;
        this.compiler = compiler;
        this.objectMapper = objectMapper;
    }

    /**
     * 部署 Plan 到 Zeebe。
     */
    public CampaignPlan deploy(String planId) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        if (plan.getGraphJson() == null) {
            throw new IllegalStateException("Plan has no canvas DAG: " + planId);
        }

        try {
            // 1. 编译 BPMN
            CanvasDag dag = objectMapper.readValue(plan.getGraphJson(), CanvasDag.class);
            String bpmnXml = compiler.compile(dag);

            // 2. 模拟部署（生产：zeebeClient.newDeployResourceCommand()）
            deployCounter++;
            String processId = "campaign_process_" + planId;
            int version = 1;

            // 3. 更新 Plan
            plan.setZeebeProcessId(processId);
            plan.setZeebeVersion(version);
            plan.setStatus("EXECUTING");
            plan.setUpdatedAt(LocalDateTime.now());
            plan = planRepo.save(plan);

            log.info("Zeebe deployment simulated: planId={}, processId={}, version={}, bpmnSize={}",
                    planId, processId, version, bpmnXml.length());
            return plan;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deploy plan to Zeebe: " + e.getMessage(), e);
        }
    }

    public int getDeployCount() {
        return deployCounter;
    }
}
