package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 积分发放 Worker。
 *
 * <p>Zeebe Job Type: {@code campaign-offer-points}
 */
@Component
public class OfferPointsWorker extends BaseCampaignWorker {

    public OfferPointsWorker(InterventionService interventionService) {
        super(interventionService);
    }

    @Override
    public String getJobType() {
        return "campaign-offer-points";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> variables) {
        List<String> memberIds = (List<String>) variables.get("memberIds");
        String pointType = getString(variables, "pointType");
        BigDecimal amount = BigDecimal.valueOf(getDouble(variables, "amount"));

        log.info("OfferPoints: type={}, amount={}, members={}", pointType, amount,
                memberIds != null ? memberIds.size() : 0);

        if (memberIds == null || memberIds.isEmpty()) {
            return result("granted", 0);
        }

        // 模拟积分发放（生产环境调用 PointGrantService）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("pointType", pointType);
        result.put("amount", amount);
        result.put("memberCount", memberIds.size());
        result.put("totalPoints", amount.multiply(BigDecimal.valueOf(memberIds.size())));
        return result;
    }
}
