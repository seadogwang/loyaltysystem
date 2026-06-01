package com.loyalty.saas.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.saas.domain.entity.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序模板消息提供者。
 *
 * <p>调用微信 API {@code sendTemplateMessage} 向用户推送模板消息。
 * 需要先通过 code2Session 获取 openId（隐含在 recipient 字段中）。
 */
@Component
public class WeChatNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(WeChatNotificationProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override public String getChannel() { return "WECHAT_TEMPLATE"; }

    @Override
    public SendResult send(NotificationOutbox outbox) throws Exception {
        String openId = outbox.getRecipient();
        String accessToken = getAccessToken();

        // 构造模板消息
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openId);
        body.put("template_id", outbox.getTemplateCode());
        body.put("page", outbox.getPayload().getOrDefault("page", "pages/index/index"));

        Map<String, Map<String, String>> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : outbox.getPayload().entrySet()) {
            if (!"page".equals(e.getKey())) {
                data.put(e.getKey(), Map.of("value", String.valueOf(e.getValue())));
            }
        }
        body.put("data", data);

        String json = mapper.writeValueAsString(body);
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            Map<String, Object> respBody = mapper.readValue(response.body(), Map.class);
            if (respBody != null && respBody.containsKey("errmsg") && "ok".equals(respBody.get("errmsg"))) {
                log.info("[WeChat] 模板消息发送成功: openId={}, template={}", openId, outbox.getTemplateCode());
                return SendResult.ok("WX-" + System.currentTimeMillis());
            }
            log.warn("[WeChat] 微信返回错误: {}", respBody);
            return SendResult.fail("WeChat error: " + respBody);
        } else {
            log.warn("[WeChat] HTTP 失败: status={}", response.statusCode());
            return SendResult.fail("WeChat HTTP " + response.statusCode());
        }
    }

    /**
     * 获取微信 access_token（骨架，实际应从缓存/Redis 获取）。
     */
    private String getAccessToken() {
        // 生产环境：Redis 缓存，定期刷新
        return System.getProperty("wechat.access.token", "mock-access-token");
    }
}