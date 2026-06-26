package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.decision.service.AttentionBudgetService;
import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 渠道消息发送 Worker — 邮件/短信/推送的统一发送。
 *
 * <p>Zeebe Job Types:
 * <ul>
 *   <li>{@code campaign-send-email}</li>
 *   <li>{@code campaign-send-sms}</li>
 *   <li>{@code campaign-send-push}</li>
 * </ul>
 */
@Component
public class SendChannelWorker extends BaseCampaignWorker {

    private final AttentionBudgetService attentionBudgetService;

    /** 渠道 → JobType 映射 */
    private static final Map<String, String> CHANNEL_TYPES = Map.of(
            "EMAIL", "campaign-send-email",
            "SMS", "campaign-send-sms",
            "PUSH", "campaign-send-push"
    );

    public SendChannelWorker(InterventionService interventionService,
                              AttentionBudgetService attentionBudgetService) {
        super(interventionService);
        this.attentionBudgetService = attentionBudgetService;
    }

    @Override
    public String getJobType() {
        return "campaign-send-channel";
    }

    /**
     * 根据 variables 中的 channel 字段路由到实际发送逻辑。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> variables) {
        String channel = getString(variables, "channel");
        List<String> memberIds = (List<String>) variables.get("memberIds");
        String assetId = getString(variables, "assetId");
        String planId = getString(variables, "planId");
        String nodeId = getString(variables, "nodeId");
        String tenantId = getString(variables, "tenantId");

        if (channel == null) channel = "EMAIL";
        if (memberIds == null) memberIds = List.of();

        log.info("SendChannel: channel={}, members={}, assetId={}", channel, memberIds.size(), assetId);

        // 注意力预算过滤
        List<String> eligible = attentionBudgetService.filterEligibleUsers(memberIds, channel);
        log.info("After attention budget filter: {}/{} eligible", eligible.size(), memberIds.size());

        // 记录曝光消耗
        for (String uid : eligible) {
            attentionBudgetService.recordExposure(uid, channel);
        }

        // 模拟发送（生产环境调用 ChannelService）
        int sentCount = eligible.size();
        int blockedCount = memberIds.size() - eligible.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("channel", channel);
        result.put("totalTargeted", memberIds.size());
        result.put("sent", sentCount);
        result.put("blockedByAttentionBudget", blockedCount);
        result.put("assetId", assetId);
        return result;
    }

    public Set<String> getSupportedJobTypes() {
        return new HashSet<>(CHANNEL_TYPES.values());
    }
}
