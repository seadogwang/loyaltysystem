package com.loyalty.platform.campaign.intervention;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Worker 防护拦截器 — 所有 Channel Worker 执行前必须检查。
 */
@Component
public class WorkerGuard {

    private static final Logger log = LoggerFactory.getLogger(WorkerGuard.class);
    private final InterventionService interventionService;

    public WorkerGuard(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    public GuardResult check(String planId, String tenantId) {
        // 1. 暂停检查
        if (interventionService.isPaused(planId)) {
            log.debug("Campaign paused: planId={}", planId);
            return GuardResult.paused();
        }
        // 2. 限流检查
        double throttleFactor = interventionService.getThrottleFactor(tenantId);
        if (throttleFactor < 1.0) {
            log.debug("Throttle active: tenantId={}, factor={}", tenantId, throttleFactor);
            return GuardResult.throttled(throttleFactor);
        }
        return GuardResult.pass();
    }

    public record GuardResult(boolean blocked, String reason, double throttleFactor) {
        public boolean shouldSkip() { return blocked; }
        public static GuardResult pass() { return new GuardResult(false, null, 1.0); }
        public static GuardResult paused() { return new GuardResult(true, "CAMPAIGN_PAUSED", 1.0); }
        public static GuardResult throttled(double factor) { return new GuardResult(false, "THROTTLED", factor); }
    }
}
