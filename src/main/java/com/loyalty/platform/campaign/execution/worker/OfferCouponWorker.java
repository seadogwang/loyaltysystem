package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券发放 Worker。
 *
 * <p>Zeebe Job Type: {@code campaign-offer-coupon}
 */
@Component
public class OfferCouponWorker extends BaseCampaignWorker {

    public OfferCouponWorker(InterventionService interventionService) {
        super(interventionService);
    }

    @Override
    public String getJobType() {
        return "campaign-offer-coupon";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> variables) {
        List<String> memberIds = (List<String>) variables.get("memberIds");
        String couponId = getString(variables, "couponId");
        Integer count = getInt(variables, "count");

        log.info("OfferCoupon: couponId={}, count={}, members={}", couponId, count,
                memberIds != null ? memberIds.size() : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("couponId", couponId);
        result.put("issuedCount", memberIds != null ? memberIds.size() * (count != null ? count : 1) : 0);
        return result;
    }
}
