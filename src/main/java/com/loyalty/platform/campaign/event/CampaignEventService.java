package com.loyalty.platform.campaign.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Campaign 事件服务 — 统一事件发布与反馈闭环。
 *
 * <p>开发阶段：内存队列。生产阶段：Kafka EventBridge。
 */
@Service
public class CampaignEventService {

    private static final Logger log = LoggerFactory.getLogger(CampaignEventService.class);

    // Event type constants
    public static final String PLAN_GENERATED = "CAMPAIGN_PLAN_GENERATED";
    public static final String PLAN_APPROVED = "CAMPAIGN_APPROVED";
    public static final String PLAN_DEPLOYED = "CAMPAIGN_DEPLOYED";
    public static final String PLAN_STARTED = "CAMPAIGN_STARTED";
    public static final String NODE_EXECUTED = "CAMPAIGN_NODE_EXECUTED";
    public static final String USER_EXPOSED = "CAMPAIGN_USER_EXPOSED";
    public static final String USER_ENGAGED = "CAMPAIGN_USER_ENGAGED";
    public static final String CONVERTED = "CAMPAIGN_CONVERTED";
    public static final String COMPLETED = "CAMPAIGN_COMPLETED";
    public static final String PAUSED = "CAMPAIGN_PAUSED";
    public static final String CANCELLED = "CAMPAIGN_CANCELLED";
    public static final String NODE_FAILED = "CAMPAIGN_NODE_FAILED";
    public static final String FEEDBACK_ROI = "CAMPAIGN_FEEDBACK_ROI";

    private final Queue<CampaignEvent> eventQueue = new ConcurrentLinkedQueue<>();

    /** Core publish method */
    public void publish(String eventType, String planId, Map<String, Object> payload) {
        CampaignEvent event = CampaignEvent.builder()
                .id(UUID.randomUUID().toString()).eventType(eventType)
                .planId(planId).payload(payload != null ? payload : new HashMap<>())
                .timestamp(Instant.now()).build();
        eventQueue.offer(event);
        log.info("Event published: type={}, planId={}", eventType, planId);
        triggerFeedbackLoop(event);
    }

    // ---- Convenience methods ----
    public void publishPlanGenerated(String planId, String workspaceId, String goalId) {
        publish(PLAN_GENERATED, planId, Map.of("workspaceId", workspaceId, "goalId", goalId));
    }
    public void publishPlanDeployed(String planId, String processId, int version) {
        publish(PLAN_DEPLOYED, planId, Map.of("processId", processId, "version", version));
    }
    public void publishExecutionStarted(String planId, Long instanceKey) {
        publish(PLAN_STARTED, planId, Map.of("processInstanceKey", instanceKey));
    }
    public void publishNodeExecuted(String planId, String nodeId, String nodeType,
                                     Map<String, Object> input, Map<String, Object> output,
                                     long durationMs, String status) {
        Map<String, Object> p = new HashMap<>();
        p.put("nodeId", nodeId); p.put("nodeType", nodeType);
        p.put("input", input); p.put("output", output);
        p.put("durationMs", durationMs); p.put("status", status);
        publish(NODE_EXECUTED, planId, p);
    }
    public void publishUserExposed(String planId, String userId, String channel) {
        publish(USER_EXPOSED, planId, Map.of("userId", userId, "channel", channel));
    }
    public void publishUserEngaged(String planId, String userId, String type) {
        publish(USER_ENGAGED, planId, Map.of("userId", userId, "engagementType", type));
    }
    public void publishUserConverted(String planId, String userId, String conversionType, BigDecimal amount) {
        publish(CONVERTED, planId, Map.of("userId", userId, "conversionType", conversionType, "amount", amount));
    }
    public void publishExecutionCompleted(String planId, Long instanceKey, long durationMs, long conversions) {
        publish(COMPLETED, planId, Map.of("processInstanceKey", instanceKey, "durationMs", durationMs, "totalConversions", conversions));
    }
    public void publishNodeFailed(String planId, String nodeId, String nodeType, String errorMsg, int retryCount) {
        publish(NODE_FAILED, planId, Map.of("nodeId", nodeId, "nodeType", nodeType, "errorMessage", errorMsg, "retryCount", retryCount));
    }
    public void publishFeedbackROI(String planId, BigDecimal predicted, BigDecimal actual) {
        publish(FEEDBACK_ROI, planId, Map.of("predictedROI", predicted, "actualROI", actual));
    }

    // ---- Feedback loop ----
    private void triggerFeedbackLoop(CampaignEvent event) {
        try {
            switch (event.getEventType()) {
                case NODE_EXECUTED -> {
                    Map<String, Object> p = event.getPayload();
                    if (p != null && p.containsKey("sent")) {
                        log.debug("Feedback L1: execution metrics — sent={}", p.getOrDefault("sent", 0));
                    }
                }
                case COMPLETED -> {
                    Map<String, Object> p = event.getPayload();
                    if (p != null) {
                        double predictedROI = ((Number) p.getOrDefault("predictedROI", 0.0)).doubleValue();
                        double actualROI = ((Number) p.getOrDefault("actualROI", 0.0)).doubleValue();
                        double drift = Math.abs(predictedROI - actualROI);
                        log.info("Feedback L2: model drift — predicted={}, actual={}, drift={}", predictedROI, actualROI, drift);
                        if (drift > 0.3) log.warn("Model drift threshold exceeded ({})", drift);
                    }
                }
                case PAUSED, CANCELLED ->
                    log.info("Feedback L3: strategy adjustment needed for plan={}", event.getPlanId());
            }
        } catch (Exception e) { log.warn("Feedback loop error: {}", e.getMessage()); }
    }

    public List<CampaignEvent> getPendingEvents() { return new ArrayList<>(eventQueue); }
    public void clearEvents() { eventQueue.clear(); }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class CampaignEvent {
        private String id; private String eventType; private String planId;
        private Map<String, Object> payload; private Instant timestamp;
    }
}
