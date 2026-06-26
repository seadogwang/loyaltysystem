package com.loyalty.platform.campaign.execution.service;

import com.loyalty.platform.campaign.execution.worker.BaseCampaignWorker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Worker 注册表 — 管理所有 Zeebe Worker 类型。
 */
@Component
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    private final List<BaseCampaignWorker> workers;
    private final Map<String, BaseCampaignWorker> registry = new LinkedHashMap<>();

    public WorkerRegistry(List<BaseCampaignWorker> workers) {
        this.workers = workers;
    }

    @PostConstruct
    public void init() {
        for (BaseCampaignWorker worker : workers) {
            String jobType = worker.getJobType();
            registry.put(jobType, worker);

            // SendChannelWorker 支持多个 jobType
            if (worker instanceof com.loyalty.platform.campaign.execution.worker.SendChannelWorker sw) {
                for (String type : sw.getSupportedJobTypes()) {
                    registry.put(type, worker);
                }
            }

            log.info("Worker registered: type={}, class={}", jobType, worker.getClass().getSimpleName());
        }
        log.info("Worker registry initialized: {} job types registered", registry.size());
    }

    public BaseCampaignWorker getWorker(String jobType) {
        return registry.get(jobType);
    }

    public boolean hasWorker(String jobType) {
        return registry.containsKey(jobType);
    }

    public List<String> getAllJobTypes() {
        return new ArrayList<>(registry.keySet());
    }

    public Map<String, Object> getWorkerInfo() {
        return registry.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getClass().getSimpleName()
                ));
    }
}
