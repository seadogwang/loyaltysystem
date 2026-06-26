package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级直升 Worker。
 *
 * <p>Zeebe Job Type: {@code campaign-tier-upgrade}
 */
@Component
public class TierUpgradeWorker extends BaseCampaignWorker {

    public TierUpgradeWorker(InterventionService interventionService) {
        super(interventionService);
    }

    @Override
    public String getJobType() {
        return "campaign-tier-upgrade";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> variables) {
        List<String> memberIds = (List<String>) variables.get("memberIds");
        String targetTier = getString(variables, "targetTier");

        log.info("TierUpgrade: targetTier={}, members={}", targetTier,
                memberIds != null ? memberIds.size() : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("targetTier", targetTier);
        result.put("upgradedCount", memberIds != null ? memberIds.size() : 0);
        return result;
    }
}
