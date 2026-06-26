package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 人工审批 Worker — 触发 Zeebe User Task，等待审批结果。
 *
 * <p>Zeebe Job Type: {@code campaign-approval}
 */
@Component
public class ApprovalWorker extends BaseCampaignWorker {

    public ApprovalWorker(InterventionService interventionService) {
        super(interventionService);
    }

    @Override
    public String getJobType() {
        return "campaign-approval";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> variables) {
        String planId = getString(variables, "planId");
        String nodeId = getString(variables, "nodeId");

        log.info("Approval: planId={}, nodeId={} — waiting for human approval", planId, nodeId);

        // 模拟审批：开发阶段自动通过
        // 生产阶段：Zeebe User Task 等待人工审批
        return Map.of(
                "status", "COMPLETED",
                "approved", true,
                "approvedBy", "system",
                "message", "Auto-approved (dev mode)"
        );
    }
}
