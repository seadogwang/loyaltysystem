package com.loyalty.platform.notification;

import com.loyalty.platform.domain.entity.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 短信通知提供者 — 对接第三方短信网关。
 *
 * <p>骨架实现：通过 HTTP POST 调用短信网关 API。
 * 生产环境应替换为 Feign 客户端声明式调用。
 *
 * <p>网关响应超时 5s，超时触发熔断降级。
 */
@Component
public class SmsNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationProvider.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override public String getChannel() { return "SMS"; }

    @Override
    public SendResult send(NotificationOutbox outbox) throws Exception {
        String phone = outbox.getRecipient();
        String content = buildSmsContent(outbox);

        // 构造短信网关请求
        String smsApiUrl = System.getProperty("sms.api.url", "https://sms-gateway.example.com/api/send");
        String body = String.format(
                "{\"mobile\":\"%s\",\"content\":\"%s\",\"templateCode\":\"%s\"}",
                phone, content, outbox.getTemplateCode());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(smsApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + System.getProperty("sms.api.key", "test-key"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("[SMS] 发送成功: phone={}, template={}", phone, outbox.getTemplateCode());
            return SendResult.ok("SMS-" + System.currentTimeMillis());
        } else {
            log.warn("[SMS] 发送失败: phone={}, status={}, body={}", phone, response.statusCode(), response.body());
            return SendResult.fail("SMS gateway returned " + response.statusCode());
        }
    }

    private String buildSmsContent(NotificationOutbox outbox) {
        return switch (outbox.getEventType()) {
            case "TIER_CHANGE" -> String.format("【忠诚度】恭喜您升级为%s会员！",
                    outbox.getPayload().getOrDefault("newTier", "新"));
            case "POINTS_ACCRUED" -> String.format("【忠诚度】您获得了%s积分。",
                    outbox.getPayload().getOrDefault("points", "0"));
            default -> outbox.getPayload().getOrDefault("message", "通知").toString();
        };
    }
}