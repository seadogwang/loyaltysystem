package com.loyalty.platform.campaign.decision.service;

import com.loyalty.platform.campaign.decision.DecisionErrorCode;
import com.loyalty.platform.campaign.event.CampaignEventService;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.common.exception.ResourceNotFoundException;
import com.loyalty.platform.domain.entity.campaign.CampaignBudgetAllocation;
import com.loyalty.platform.domain.entity.campaign.CampaignDecisionResult;
import com.loyalty.platform.domain.repository.campaign.CampaignBudgetAllocationRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignDecisionResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策回滚服务 — 将已应用的决策回滚到 DRAFT 状态。
 *
 * <p>回滚步骤：
 * <ol>
 *   <li>校验决策状态（仅 APPLIED 可回滚）</li>
 *   <li>标记决策为 ROLLED_BACK</li>
 *   <li>发布 ROLLBACK 事件（取消相关执行任务）</li>
 *   <li>释放注意力预算（恢复频控配额）</li>
 * </ol>
 */
@Service
@Transactional
public class DecisionRollbackService {

    private static final Logger log = LoggerFactory.getLogger(DecisionRollbackService.class);

    private final CampaignDecisionResultRepository decisionRepository;
    private final CampaignBudgetAllocationRepository allocationRepository;
    private final AttentionBudgetService attentionBudgetService;
    private final CampaignEventService eventService;

    public DecisionRollbackService(CampaignDecisionResultRepository decisionRepository,
                                    CampaignBudgetAllocationRepository allocationRepository,
                                    AttentionBudgetService attentionBudgetService,
                                    CampaignEventService eventService) {
        this.decisionRepository = decisionRepository;
        this.allocationRepository = allocationRepository;
        this.attentionBudgetService = attentionBudgetService;
        this.eventService = eventService;
    }

    /**
     * 回滚决策。
     *
     * @param decisionId 决策 ID
     * @param reason     回滚原因
     */
    public void rollback(String decisionId, String reason) {
        CampaignDecisionResult decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        DecisionErrorCode.DECISION_NOT_FOUND.getMessage()));

        if (!"APPLIED".equals(decision.getStatus())) {
            throw new BusinessException("Only APPLIED decision can be rolled back, current: "
                    + decision.getStatus());
        }

        log.info("Rolling back decision: id={}, reason={}", decisionId, reason);

        // 1. 标记决策为回滚
        decision.setStatus("ROLLED_BACK");
        decisionRepository.save(decision);

        // 2. 取消相关的分配
        List<CampaignBudgetAllocation> allocations =
                allocationRepository.findByDecisionId(decisionId);
        for (CampaignBudgetAllocation allocation : allocations) {
            if ("EXECUTING".equals(allocation.getStatus())
                    || "PENDING".equals(allocation.getStatus())) {
                allocation.setStatus("FAILED");
                allocationRepository.save(allocation);
            }
        }

        // 3. 发布回滚事件（通知 Execution Engine 取消 Zeebe 任务）
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("decisionId", decisionId);
        eventPayload.put("workspaceId", decision.getWorkspaceId());
        eventPayload.put("portfolioId", decision.getPortfolioId());
        eventPayload.put("reason", reason);
        eventService.publish("DECISION_ROLLED_BACK", decision.getPortfolioId(), eventPayload);

        // 4. 释放注意力预算
        for (CampaignBudgetAllocation allocation : allocations) {
            attentionBudgetService.releaseForDecision(allocation.getInitiativeId());
        }

        log.info("Decision rolled back successfully: id={}", decisionId);
    }

    /**
     * 回滚决策（无原因说明）。
     */
    public void rollback(String decisionId) {
        rollback(decisionId, "Manual rollback");
    }
}
