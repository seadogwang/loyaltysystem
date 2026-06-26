package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 外部 Webhook 调用 Worker。
 *
 * <p>Zeebe Job Type: {@code campaign-webhook}
 */
@Component
public class WebhookWorker extends BaseCampaignWorker {

    public WebhookWorker(InterventionService interventionService) {
        super(interventionService);
    }

    @Override
    public String getJobType() {
        return "campaign-webhook";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> variables) {
        String url = getString(variables, "url");
        String method = getString(variables, "method");
        Map<String, Object> headers = (Map<String, Object>) variables.get("headers");
        String payload = getString(variables, "payload");

        log.info("Webhook: method={}, url={}", method, url);

        // 开发阶段：模拟 Webhook 调用
        // 生产阶段：调用 WebhookService.call(url, method, headers, payload)

        return Map.of(
                "status", "COMPLETED",
                "url", url,
                "responseCode", 200,
                "responseBody", "OK (simulated)"
        );
    }
}
