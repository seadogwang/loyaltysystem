package com.loyalty.platform.campaign.execution.service;

import com.loyalty.platform.campaign.execution.ExecutionErrorCode;
import com.loyalty.platform.campaign.execution.worker.BaseCampaignWorker;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.domain.entity.campaign.CampaignPlan;
import com.loyalty.platform.domain.entity.campaign.CampaignZeebeInstance;
import com.loyalty.platform.domain.entity.campaign.CampaignZeebeTask;
import com.loyalty.platform.domain.repository.campaign.CampaignPlanRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignZeebeInstanceRepository;
import com.loyalty.platform.domain.repository.campaign.CampaignZeebeTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Zeebe 流程执行服务 — 模拟流程实例的创建和生命周期管理。
 *
 * <p>开发阶段：内存执行 + 同步调用 Workers + 持久化到 DB。
 * 生产阶段：ZeebeClient.createProcessInstance() + 异步 Workers。
 */
@Service
@Transactional
public class ZeebeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ZeebeExecutionService.class);

    private final Map<Long, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final AtomicLong instanceKeyGen = new AtomicLong(1000);

    private final CampaignPlanRepository planRepo;
    private final CampaignZeebeInstanceRepository zeebeInstanceRepo;
    private final CampaignZeebeTaskRepository zeebeTaskRepo;
    private final WorkerRegistry workerRegistry;

    public ZeebeExecutionService(CampaignPlanRepository planRepo,
                                  CampaignZeebeInstanceRepository zeebeInstanceRepo,
                                  CampaignZeebeTaskRepository zeebeTaskRepo,
                                  WorkerRegistry workerRegistry) {
        this.planRepo = planRepo;
        this.zeebeInstanceRepo = zeebeInstanceRepo;
        this.zeebeTaskRepo = zeebeTaskRepo;
        this.workerRegistry = workerRegistry;
    }

    // ========================================================================
    // 流程实例生命周期
    // ========================================================================

    public ProcessInstance createInstance(String planId) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new BusinessException(ExecutionErrorCode.PLAN_NOT_FOUND.getMessage()));

        if (!"EXECUTING".equals(plan.getStatus()) && plan.getStatus() != null
                && !"RUNNING".equals(plan.getStatus())) {
            throw new BusinessException(ExecutionErrorCode.PLAN_NOT_DEPLOYED.getMessage());
        }

        long instanceKey = instanceKeyGen.incrementAndGet();
        String bpmnProcessId = plan.getZeebeProcessId() != null
                ? plan.getZeebeProcessId() : "campaign_process_" + planId;

        ProcessInstance instance = ProcessInstance.builder()
                .instanceKey(instanceKey).bpmnProcessId(bpmnProcessId)
                .planId(planId).version(plan.getZeebeVersion() != null ? plan.getZeebeVersion() : 1)
                .state("ACTIVE").variables(new HashMap<>())
                .startTime(LocalDateTime.now()).build();
        instances.put(instanceKey, instance);

        plan.setZeebeInstanceKey(instanceKey);
        plan.setStatus("EXECUTING");
        plan.setUpdatedAt(LocalDateTime.now());
        planRepo.save(plan);

        // Persist Zeebe instance
        CampaignZeebeInstance zi = CampaignZeebeInstance.builder()
                .id(UUID.randomUUID().toString()).planId(planId)
                .processInstanceKey(instanceKey).bpmnProcessId(bpmnProcessId)
                .version(plan.getZeebeVersion()).status("RUNNING")
                .variables(new HashMap<>()).startTime(Instant.now()).build();
        zeebeInstanceRepo.save(zi);

        log.info("Process instance created: key={}, planId={}", instanceKey, planId);
        return instance;
    }

    public Map<String, Object> executeNode(long instanceKey, String jobType, Map<String, Object> variables) {
        ProcessInstance instance = getInstance(instanceKey);
        if (instance == null) throw new IllegalArgumentException("Instance not found: " + instanceKey);

        BaseCampaignWorker worker = workerRegistry.getWorker(jobType);
        if (worker == null) throw new IllegalArgumentException("No worker for job type: " + jobType);

        Map<String, Object> mergedVars = new HashMap<>(instance.getVariables());
        if (variables != null) mergedVars.putAll(variables);

        // Persist task start
        String taskId = UUID.randomUUID().toString();
        CampaignZeebeTask task = CampaignZeebeTask.builder()
                .id(taskId).instanceId(findZeebeInstanceId(instanceKey))
                .planId(instance.getPlanId()).jobKey(instanceKey * 100 + instance.getExecutionHistory().size())
                .taskType(jobType).status("CREATED")
                .inputVariables(new HashMap<>(mergedVars)).startTime(Instant.now()).build();
        zeebeTaskRepo.save(task);

        try {
            Map<String, Object> result = worker.handle(mergedVars);
            instance.getVariables().putAll(result);
            instance.setLastActivityTime(LocalDateTime.now());

            ExecutionRecord record = ExecutionRecord.builder()
                    .id(UUID.randomUUID().toString()).instanceKey(instanceKey)
                    .jobType(jobType).input(mergedVars).output(result).executedAt(LocalDateTime.now()).build();
            instance.getExecutionHistory().add(record);

            // Update task to COMPLETED
            task.setStatus("COMPLETED");
            task.setOutputVariables(result);
            task.setEndTime(Instant.now());
            var start = task.getStartTime();
            if (start != null) task.setDurationMs(Instant.now().toEpochMilli() - start.toEpochMilli());
            zeebeTaskRepo.save(task);

            log.info("Node executed: instanceKey={}, jobType={}", instanceKey, jobType);
            return result;
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setEndTime(Instant.now());
            zeebeTaskRepo.save(task);
            log.error("Node execution failed: instanceKey={}, jobType={}", instanceKey, jobType, e);
            throw new RuntimeException("Worker failed: " + e.getMessage(), e);
        }
    }

    public ProcessInstance completeInstance(long instanceKey) {
        ProcessInstance instance = getInstance(instanceKey);
        if (instance == null) throw new IllegalArgumentException("Instance not found: " + instanceKey);
        instance.setState("COMPLETED");
        instance.setEndTime(LocalDateTime.now());

        planRepo.findById(instance.getPlanId()).ifPresent(plan -> {
            plan.setStatus("COMPLETED"); plan.setUpdatedAt(LocalDateTime.now()); planRepo.save(plan);
        });
        zeebeInstanceRepo.updateStatusByPlanId(instance.getPlanId(), "COMPLETED");

        log.info("Process instance completed: key={}", instanceKey);
        return instance;
    }

    public ProcessInstance cancelInstance(long instanceKey) {
        ProcessInstance instance = getInstance(instanceKey);
        if (instance == null) throw new IllegalArgumentException("Instance not found: " + instanceKey);
        instance.setState("CANCELLED");
        instance.setEndTime(LocalDateTime.now());

        planRepo.findById(instance.getPlanId()).ifPresent(plan -> {
            plan.setStatus("CANCELLED"); plan.setUpdatedAt(LocalDateTime.now()); planRepo.save(plan);
        });
        zeebeInstanceRepo.updateStatusByPlanId(instance.getPlanId(), "CANCELLED");

        log.info("Process instance cancelled: key={}", instanceKey);
        return instance;
    }

    // ========================================================================
    // 暂停/恢复
    // ========================================================================

    public ProcessInstance pauseExecution(String planId) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new BusinessException(ExecutionErrorCode.PLAN_NOT_FOUND.getMessage()));
        if (!"EXECUTING".equals(plan.getStatus()) && !"RUNNING".equals(plan.getStatus())) {
            throw new IllegalStateException("Only RUNNING plan can be paused");
        }
        var inst = instances.values().stream().filter(i -> i.getPlanId().equals(planId)).findFirst();
        inst.ifPresent(i -> i.setState("PAUSED"));
        plan.setStatus("PAUSED"); plan.setUpdatedAt(LocalDateTime.now()); planRepo.save(plan);
        zeebeInstanceRepo.updateStatusByPlanId(planId, "PAUSED");
        log.info("Execution paused: planId={}", planId);
        return inst.orElse(null);
    }

    public ProcessInstance resumeExecution(String planId) {
        CampaignPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new BusinessException(ExecutionErrorCode.PLAN_NOT_FOUND.getMessage()));
        if (!"PAUSED".equals(plan.getStatus())) {
            throw new IllegalStateException("Only PAUSED plan can be resumed");
        }
        var inst = instances.values().stream().filter(i -> i.getPlanId().equals(planId)).findFirst();
        inst.ifPresent(i -> i.setState("ACTIVE"));
        plan.setStatus("EXECUTING"); plan.setUpdatedAt(LocalDateTime.now()); planRepo.save(plan);
        zeebeInstanceRepo.updateStatusByPlanId(planId, "RUNNING");
        log.info("Execution resumed: planId={}", planId);
        return inst.orElse(null);
    }

    // ========================================================================
    // 查询
    // ========================================================================

    public ProcessInstance getInstance(long instanceKey) { return instances.get(instanceKey); }

    public ProcessInstance getInstanceByPlan(String planId) {
        return instances.values().stream().filter(i -> i.getPlanId().equals(planId)).findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public ExecutionStatus getExecutionStatus(String planId) {
        CampaignPlan plan = planRepo.findById(planId).orElse(null);
        if (plan == null) return null;

        var inst = instances.values().stream().filter(i -> i.getPlanId().equals(planId)).findFirst();
        int completed = inst.map(i -> i.getExecutionHistory().size()).orElse(0);
        List<CampaignZeebeTask> recentTasks = zeebeTaskRepo.findTop10ByPlanIdOrderByStartTimeDesc(planId);

        return ExecutionStatus.builder()
                .planId(planId).processInstanceKey(plan.getZeebeInstanceKey())
                .status(inst.map(ProcessInstance::getState).orElse(plan.getStatus()))
                .startTime(inst.map(ProcessInstance::getStartTime).orElse(null))
                .completedNodes(completed).recentTasks(recentTasks).build();
    }

    private String findZeebeInstanceId(long instanceKey) {
        ProcessInstance pi = instances.get(instanceKey);
        if (pi != null) {
            var zi = zeebeInstanceRepo.findByPlanId(pi.getPlanId());
            if (zi.isPresent()) return zi.get().getId();
        }
        return "unknown";
    }

    // ========================================================================
    // 内部数据类
    // ========================================================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProcessInstance {
        private long instanceKey;
        private String bpmnProcessId;
        private String planId;
        private int version;
        private String state;
        private Map<String, Object> variables;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime lastActivityTime;
        @lombok.Builder.Default
        private List<ExecutionRecord> executionHistory = new ArrayList<>();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExecutionRecord {
        private String id;
        private long instanceKey;
        private String jobType;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private LocalDateTime executedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExecutionStatus {
        private String planId;
        private Long processInstanceKey;
        private String status;
        private LocalDateTime startTime;
        private int completedNodes;
        private List<CampaignZeebeTask> recentTasks;
    }
}
