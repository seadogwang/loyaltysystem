package com.loyalty.platform.campaign.planning.service;

import com.loyalty.platform.campaign.planning.dto.CreateInitiativeRequest;
import com.loyalty.platform.campaign.planning.dto.CreateKpiRequest;
import com.loyalty.platform.campaign.planning.dto.InitiativeContext;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.common.exception.ResourceNotFoundException;
import com.loyalty.platform.domain.entity.campaign.*;
import com.loyalty.platform.domain.repository.campaign.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 举措管理服务。
 */
@Service
@Transactional
public class InitiativeService {

    private static final Logger log = LoggerFactory.getLogger(InitiativeService.class);

    private final CampaignInitiativeRepository initiativeRepository;
    private final CampaignInitiativeKpiRepository kpiRepository;
    private final CampaignInitiativePlanRelationRepository planRelationRepository;
    private final GoalService goalService;
    private final WorkspaceLockService lockService;

    public InitiativeService(CampaignInitiativeRepository initiativeRepository,
                             CampaignInitiativeKpiRepository kpiRepository,
                             CampaignInitiativePlanRelationRepository planRelationRepository,
                             GoalService goalService,
                             WorkspaceLockService lockService) {
        this.initiativeRepository = initiativeRepository;
        this.kpiRepository = kpiRepository;
        this.planRelationRepository = planRelationRepository;
        this.goalService = goalService;
        this.lockService = lockService;
    }

    /**
     * 创建 Initiative（PLANNED 状态）。
     */
    public CampaignInitiative createInitiative(CreateInitiativeRequest request) {
        // 校验 Goal 存在
        CampaignGoal goal = goalService.getGoal(request.getGoalId());

        CampaignInitiative initiative = CampaignInitiative.builder()
                .id(UUID.randomUUID().toString())
                .workspaceId(goal.getWorkspaceId())
                .goalId(request.getGoalId())
                .name(request.getName())
                .description(request.getDescription())
                .initiativeType(request.getInitiativeType())
                .status("PLANNED")
                .priority(request.getPriority() != null ? request.getPriority() : 100)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .ruleConfig(request.getRuleConfig())
                .createdBy(getCurrentUserId())
                .build();
        initiative = initiativeRepository.save(initiative);

        // 创建 KPI
        if (request.getKpis() != null) {
            for (CreateKpiRequest kpiReq : request.getKpis()) {
                CampaignInitiativeKpi kpi = CampaignInitiativeKpi.builder()
                        .id(UUID.randomUUID().toString())
                        .initiativeId(initiative.getId())
                        .kpiType(kpiReq.getKpiType())
                        .targetValue(kpiReq.getTargetValue())
                        .weight(kpiReq.getWeight() != null ? kpiReq.getWeight() : BigDecimal.ONE)
                        .build();
                kpiRepository.save(kpi);
            }
        }

        log.info("Initiative created: id={}, name={}, goal={}",
                initiative.getId(), initiative.getName(), initiative.getGoalId());
        return initiative;
    }

    /**
     * 获取 Initiative。
     */
    @Transactional(readOnly = true)
    public CampaignInitiative getInitiative(String initiativeId) {
        return initiativeRepository.findById(initiativeId)
                .orElseThrow(() -> new ResourceNotFoundException("Initiative not found: " + initiativeId));
    }

    /**
     * 获取 Goal 下所有 Initiative。
     */
    @Transactional(readOnly = true)
    public List<CampaignInitiative> getInitiativesByGoal(String goalId) {
        return initiativeRepository.findByGoalId(goalId);
    }

    /**
     * 获取 Workspace 下所有 Initiative。
     */
    @Transactional(readOnly = true)
    public List<CampaignInitiative> getInitiativesByWorkspace(String workspaceId) {
        return initiativeRepository.findByWorkspaceId(workspaceId);
    }

    /**
     * 获取 Workspace 下所有 ACTIVE Initiative。
     */
    @Transactional(readOnly = true)
    public List<CampaignInitiative> getActiveInitiatives(String workspaceId) {
        return initiativeRepository.findActiveByWorkspaceId(workspaceId);
    }

    /**
     * 激活 Initiative（需校验 Goal 为 ACTIVE）。
     */
    public CampaignInitiative activateInitiative(String initiativeId) {
        CampaignInitiative initiative = getInitiative(initiativeId);
        if (!"PLANNED".equals(initiative.getStatus()) && !"PAUSED".equals(initiative.getStatus())) {
            throw new BusinessException("ERR_INITIATIVE_CANNOT_ACTIVATE", "Only PLANNED or PAUSED initiative can be activated");
        }

        // 校验 Goal 是否为 ACTIVE
        CampaignGoal goal = goalService.getGoal(initiative.getGoalId());
        if (!"ACTIVE".equals(goal.getStatus())) {
            throw new BusinessException("ERR_INITIATIVE_GOAL_NOT_ACTIVE", "Cannot activate initiative: Goal is not ACTIVE");
        }

        initiative.setStatus("ACTIVE");
        initiative.setUpdatedAt(LocalDateTime.now());
        initiative = initiativeRepository.save(initiative);

        log.info("Initiative activated: id={}, goal={}", initiative.getId(), initiative.getGoalId());
        return initiative;
    }

    /**
     * 暂停 Initiative。
     */
    public CampaignInitiative pauseInitiative(String initiativeId) {
        CampaignInitiative initiative = getInitiative(initiativeId);
        if (!"ACTIVE".equals(initiative.getStatus())) {
            throw new BusinessException("ERR_INITIATIVE_CANNOT_PAUSE", "Only ACTIVE initiative can be paused");
        }
        initiative.setStatus("PAUSED");
        initiative.setUpdatedAt(LocalDateTime.now());
        initiative = initiativeRepository.save(initiative);
        log.info("Initiative paused: id={}", initiativeId);
        return initiative;
    }

    /**
     * 完成 Initiative。
     */
    public CampaignInitiative completeInitiative(String initiativeId) {
        CampaignInitiative initiative = getInitiative(initiativeId);
        if (!"ACTIVE".equals(initiative.getStatus()) && !"PAUSED".equals(initiative.getStatus())) {
            throw new BusinessException("ERR_INITIATIVE_CANNOT_COMPLETE", "Only ACTIVE or PAUSED initiative can be completed");
        }
        initiative.setStatus("COMPLETED");
        initiative.setUpdatedAt(LocalDateTime.now());
        initiative = initiativeRepository.save(initiative);
        log.info("Initiative completed: id={}", initiativeId);
        return initiative;
    }

    /**
     * 绑定 Campaign Plan 到 Initiative。
     */
    public CampaignInitiativePlanRelation bindPlan(String initiativeId, String planId,
                                                    String role, BigDecimal weight) {
        getInitiative(initiativeId);

        CampaignInitiativePlanRelation relation = CampaignInitiativePlanRelation.builder()
                .id(UUID.randomUUID().toString())
                .initiativeId(initiativeId)
                .planId(planId)
                .role(role != null ? role : "PRIMARY")
                .weight(weight != null ? weight : BigDecimal.ONE)
                .build();
        relation = planRelationRepository.save(relation);

        log.info("Plan bound to initiative: initiative={}, plan={}, role={}",
                initiativeId, planId, role);
        return relation;
    }

    /**
     * 解绑 Initiative 和 Plan。
     */
    public void unbindPlan(String initiativeId, String planId) {
        planRelationRepository.deleteByInitiativeIdAndPlanId(initiativeId, planId);
        log.info("Plan unbound from initiative: initiative={}, plan={}", initiativeId, planId);
    }

    /**
     * 获取 Initiative 上下文（含 KPI 和绑定的 Plans）。
     */
    @Transactional(readOnly = true)
    public InitiativeContext loadContext(String initiativeId) {
        CampaignInitiative initiative = getInitiative(initiativeId);
        List<CampaignInitiativeKpi> kpis = kpiRepository.findByInitiativeId(initiativeId);
        List<CampaignInitiativePlanRelation> relations = planRelationRepository.findByInitiativeId(initiativeId);

        return InitiativeContext.builder()
                .initiative(initiative)
                .kpis(kpis)
                .planRelations(relations)
                .build();
    }

    /** 获取当前用户 ID */
    private String getCurrentUserId() {
        // TODO: 替换为真实的 SecurityContext.getCurrentUserId()
        return "system";
    }
}
