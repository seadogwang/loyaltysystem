package com.loyalty.saas.notification;

import com.loyalty.saas.domain.entity.NotificationOutbox;

/**
 * 触达通道提供者策略接口。
 *
 * <p>实现类负责与具体第三方网关交互（短信/微信/推送）。
 * 每个实现对应 NotificationOutbox.channel 的一个值。
 */
public interface NotificationProvider {

    /** 支持的渠道标识 */
    String getChannel();

    /**
     * 发送通知。
     *
     * @param outbox 待发送的通知流水
     * @return SendResult 发送结果
     * @throws Exception 网络异常、超时、频率限制等
     */
    SendResult send(NotificationOutbox outbox) throws Exception;

    /** 发送结果 */
    record SendResult(boolean success, String providerMessageId, String errorMessage) {
        public static SendResult ok(String messageId) {
            return new SendResult(true, messageId, null);
        }
        public static SendResult fail(String error) {
            return new SendResult(false, null, error);
        }
    }
}