package com.loyalty.platform.campaign.intervention.service;

import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.common.exception.ResourceNotFoundException;
import com.loyalty.platform.domain.entity.campaign.CampaignInterventionCommand;
import com.loyalty.platform.domain.entity.campaign.CampaignPlan;
import com.loyalty.platform.domain.repository.campaign.CampaignInterventionCommandRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工干预服务 — 暂停/恢复/取消/跳过节点/覆盖配置/限流。
 *
 * <p>开发阶段：基于内存状态 + 数据库记录。
 * 生产阶段：接入 Zeebe 流程实例控制 + Redis 限流广播。
 */
@Service
public class InterventionService {

    private static final Logger log = LoggerFactory.getLogger(InterventionService.class);

    /** 内存中的计划运行状态（生产用 Zeebe） */
    private final Map<String, String> planStatusMap = new ConcurrentHashMap<>();
    /** 内存中的节点跳过列表 */
    private final Map<String, Set<String>> skippedNodesMap = new ConcurrentHashMap<>();
    /** 内存中的节点配置覆盖 */
    private final Map<String, Map<String, Object>> nodeConfigOverrides = new ConcurrentHashMap<>();
    /** 租户限流系数（1.0 = 无限制，0.0 = 完全阻断） */
    private final Map<String, Double> throttleMap = new ConcurrentHashMap<>();

    private final CampaignInterventionCommandRepository commandRepo;
    private final CampaignPlanRepository planRepo;

    public InterventionService(CampaignInterventionCommandRepository commandRepo,
                                CampaignPlanRepository planRepo) {
        this.commandRepo = commandRepo;
        this.planRepo = planRepo;
    }

    /** 暂停活动 */
    @Transactional
    public CampaignInterventionCommand pauseCampaign(String planId, String operatorId, String reason) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        planStatusMap.put(planId, "PAUSED_BY_USER");
        plan.setStatus("PAUSED_BY_USER");
        planRepo.save(plan);

        CampaignInterventionCommand cmd = buildCommand(planId, null, "PAUSE", operatorId, reason, null, null);
        cmd.setExecutedAt(LocalDateTime.now());
        cmd = commandRepo.save(cmd);

        log.info("Campaign paused: planId={}, operator={}, reason={}", planId, operatorId, reason);
        return cmd;
    }

    /** 恢复活动 */
    @Transactional
    public CampaignInterventionCommand resumeCampaign(String planId, String operatorId, String reason) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        if (!"PAUSED_BY_USER".equals(planStatusMap.getOrDefault(planId, plan.getStatus()))) {
            throw new BusinessException("ERR_NOT_PAUSED", "Campaign is not paused");
        }

        planStatusMap.put(planId, "EXECUTING");
        plan.setStatus("EXECUTING");
        planRepo.save(plan);

        CampaignInterventionCommand cmd = buildCommand(planId, null, "RESUME", operatorId, reason, null, null);
        cmd.setExecutedAt(LocalDateTime.now());
        cmd = commandRepo.save(cmd);

        log.info("Campaign resumed: planId={}, operator={}", planId, operatorId);
        return cmd;
    }

    /** 取消活动 */
    @Transactional
    public CampaignInterventionCommand cancelCampaign(String planId, String operatorId, String reason) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        planStatusMap.put(planId, "CANCELLED");
        plan.setStatus("CANCELLED");
        planRepo.save(plan);

        CampaignInterventionCommand cmd = buildCommand(planId, null, "CANCEL", operatorId, reason, null, null);
        cmd.setExecutedAt(LocalDateTime.now());
        cmd = commandRepo.save(cmd);

        log.info("Campaign cancelled: planId={}, operator={}", planId, operatorId);
        return cmd;
    }

    /** 跳过指定节点 */
    @Transactional
    public CampaignInterventionCommand skipNode(String planId, String nodeId, String operatorId, String reason) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        skippedNodesMap.computeIfAbsent(planId, k -> ConcurrentHashMap.newKeySet()).add(nodeId);

        CampaignInterventionCommand cmd = buildCommand(planId, nodeId, "SKIP_NODE", operatorId, reason, null, null);
        cmd.setExecutedAt(LocalDateTime.now());
        cmd = commandRepo.save(cmd);

        log.info("Node skipped: planId={}, nodeId={}, operator={}", planId, nodeId, operatorId);
        return cmd;
    }

    /** 覆盖节点配置 */
    @Transactional
    public CampaignInterventionCommand overrideNodeConfig(String planId, String nodeId,
                                                           Map<String, Object> newConfig,
                                                           String operatorId, String reason) {
        String configKey = planId + ":" + nodeId;
        Map<String, Object> previousConfig = nodeConfigOverrides.get(configKey);

        nodeConfigOverrides.put(configKey, newConfig);

        CampaignInterventionCommand cmd = buildCommand(planId, nodeId, "UPDATE_CONFIG", operatorId, reason,
                previousConfig != null ? previousConfig.toString() : null,
                newConfig.toString());
        cmd.setExecutedAt(LocalDateTime.now());
        cmd = commandRepo.save(cmd);

        log.info("Node config overridden: planId={}, nodeId={}, operator={}", planId, nodeId, operatorId);
        return cmd;
    }

    /** 紧急限流 */
    public void emergencyThrottle(String tenantId, double factor) {
        if (factor < 0 || factor > 1) {
            throw new BusinessException("ERR_INVALID_THROTTLE", "Throttle factor must be between 0 and 1");
        }
        throttleMap.put(tenantId, factor);
        log.warn("Emergency throttle applied: tenant={}, factor={}", tenantId, factor);
    }

    /** 取消限流 */
    public void removeThrottle(String tenantId) {
        throttleMap.remove(tenantId);
        log.info("Throttle removed: tenant={}", tenantId);
    }

    /** 检查活动是否已暂停 */
    @Transactional(readOnly = true)
    public boolean isPaused(String planId) {
        String status = planStatusMap.get(planId);
        return "PAUSED_BY_USER".equals(status) || "CANCELLED".equals(status);
    }

    /** 检查节点是否应被跳过 */
    public boolean isNodeSkipped(String planId, String nodeId) {
        Set<String> skipped = skippedNodesMap.get(planId);
        return skipped != null && skipped.contains(nodeId);
    }

    /** 获取节点配置覆盖值 */
    public Map<String, Object> getNodeConfigOverride(String planId, String nodeId) {
        return nodeConfigOverrides.get(planId + ":" + nodeId);
    }

    /** 获取当前限流系数 */
    public double getThrottleFactor(String tenantId) {
        return throttleMap.getOrDefault(tenantId, 1.0);
    }

    /** Worker 防护钩子 — 执行前检查 */
    public void checkBeforeExecution(String planId, String nodeId, String tenantId) {
        if (isPaused(planId)) {
            throw new BusinessException("ERR_CAMPAIGN_PAUSED", "Campaign " + planId + " is paused");
        }
        if (isNodeSkipped(planId, nodeId)) {
            throw new BusinessException("ERR_NODE_SKIPPED", "Node " + nodeId + " is skipped");
        }
        double throttle = getThrottleFactor(tenantId);
        if (throttle < 0.5) {
            throw new BusinessException("ERR_THROTTLED", "Throttle active: factor=" + throttle);
        }
    }

    /** 获取干预历史 */
    @Transactional(readOnly = true)
    public List<CampaignInterventionCommand> getInterventions(String planId) {
        return commandRepo.findByPlanIdOrderByCreatedAtDesc(planId);
    }

    /** 获取活动运行状态 */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlanStatus(String planId) {
        CampaignPlan plan = planRepo.findById(planId).orElse(null);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("planId", planId);
        status.put("status", planStatusMap.getOrDefault(planId, plan != null ? plan.getStatus() : "UNKNOWN"));
        status.put("paused", isPaused(planId));
        status.put("skippedNodes", skippedNodesMap.getOrDefault(planId, Collections.emptySet()));
        status.put("throttleFactor", getThrottleFactor(planId));
        return status;
    }

    private CampaignInterventionCommand buildCommand(String planId, String nodeId,
                                                      String commandType, String operatorId,
                                                      String reason, String previousSnapshot,
                                                      String newSnapshot) {
        return CampaignInterventionCommand.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .targetNodeId(nodeId)
                .commandType(commandType)
                .operatorId(operatorId)
                .reason(reason)
                .previousStateSnapshot(previousSnapshot)
                .newConfigSnapshot(newSnapshot)
                .build();
    }
}
