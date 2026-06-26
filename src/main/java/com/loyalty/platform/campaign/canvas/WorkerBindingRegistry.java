package com.loyalty.platform.campaign.canvas;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker 绑定注册表 — Node Type → Zeebe Worker Type 映射。
 *
 * <p>编译器通过此注册表将 Canvas 节点类型映射到对应的 Zeebe Worker Type，
 * 生成正确的 zeebe:taskType 属性。
 */
@Component
public class WorkerBindingRegistry {

    private final Map<String, String> nodeTypeToWorker = new ConcurrentHashMap<>();
    private final Map<String, String> workerToHandler = new ConcurrentHashMap<>();

    public WorkerBindingRegistry() {
        register("AUDIENCE_FILTER", "campaign-audience-filter", "AudienceFilterWorker");
        register("AI_SCORE", "campaign-ai-score", "AiScoreWorker");
        register("AI_PLANNER", "campaign-ai-planner", "AiPlannerWorker");
        register("SEND_EMAIL", "campaign-send-email", "SendChannelWorker");
        register("SEND_SMS", "campaign-send-sms", "SendChannelWorker");
        register("SEND_PUSH", "campaign-send-push", "SendChannelWorker");
        register("OFFER_POINTS", "campaign-offer-points", "OfferPointsWorker");
        register("OFFER_COUPON", "campaign-offer-coupon", "OfferCouponWorker");
        register("TIER_UPGRADE", "campaign-tier-upgrade", "TierUpgradeWorker");
        register("WEBHOOK", "campaign-webhook", "WebhookWorker");
        register("APPROVAL", "campaign-approval", "ApprovalWorker");
        register("DELAY", "campaign-delay", null);
        register("WAIT_EVENT", "campaign-wait-event", null);
        register("DEFAULT", "campaign-default", null);
    }

    public void register(String nodeType, String workerType, String handlerClass) {
        nodeTypeToWorker.put(nodeType, workerType);
        if (handlerClass != null) workerToHandler.put(workerType, handlerClass);
    }

    public String getWorkerType(String nodeType) {
        return nodeTypeToWorker.getOrDefault(nodeType, "campaign-default");
    }

    public String getHandlerClass(String workerType) {
        return workerToHandler.get(workerType);
    }

    public boolean isRegistered(String nodeType) {
        return nodeTypeToWorker.containsKey(nodeType);
    }

    public Map<String, String> getAllMappings() {
        return Map.copyOf(nodeTypeToWorker);
    }
}
