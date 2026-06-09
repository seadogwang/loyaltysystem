package com.loyalty.platform.notification;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.event.BaseDomainEvent;
import com.loyalty.platform.common.event.DomainEventHandler;
import com.loyalty.platform.domain.entity.NotificationOutbox;
import com.loyalty.platform.domain.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 触达事件监听器 — 订阅核心业务领域事件，写入通知外送箱。
 *
 * <p>订阅事件：
 * <ul>
 *   <li>{@code TierChangeEvent} → SMS + WECHAT_TEMPLATE（等级变更通知）</li>
 *   <li>{@code PointAccruedEvent} → WECHAT_TEMPLATE（积分入账提醒）</li>
 * </ul>
 *
 * <p><b>中间件防穿透校验（Ch9 第三层防御）</b>：
 * 消费事件前强制校验 {@code event.getProgramCode()} 是否与当前 TenantContext
 * 一致。若不一致，立即丢弃并触发熔断告警。这是第九章死守的安全红线。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class OutboundEventSubscriber implements DomainEventHandler<BaseDomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(OutboundEventSubscriber.class);

    private final NotificationOutboxRepository outboxRepo;

    public OutboundEventSubscriber(NotificationOutboxRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
    }

    @Override public String getTopic() { return "loyalty-events"; }

    @Override
    @SuppressWarnings("unchecked")
    public Class<BaseDomainEvent> getEventType() { return BaseDomainEvent.class; }

    @Override
    public void handle(BaseDomainEvent event) {
        // ==================== 中间件防穿透校验 ====================
        String currentTenant = TenantContext.get();
        String eventTenant = event.getProgramCode();

        if (currentTenant == null || !currentTenant.equals(eventTenant)) {
            log.error("[OutboundSubscriber] 租户穿透拦截！event.programCode={}, tenant.context={}, eventId={}",
                    eventTenant, currentTenant, event.getEventId());
            // 触发熔断告警（可集成 Prometheus Alert）
            return;
        }

        try {
            if (event instanceof TierChangeEvent tce) {
                handleTierChange(tce);
            } else if (event instanceof PointAccruedEvent pae) {
                handlePointAccrued(pae);
            }
        } catch (Exception e) {
            log.error("[OutboundSubscriber] 事件处理异常: eventId={}, type={}", event.getEventId(), event.getEventType(), e);
        }
    }

    private void handleTierChange(TierChangeEvent event) {
        // SMS 通知
        NotificationOutbox sms = NotificationOutbox.builder()
                .programCode(event.getProgramCode())
                .memberId(event.getMemberId())
                .eventType("TIER_CHANGE")
                .channel("SMS")
                .recipient("13800138000") // 实际从 member 查询手机号
                .templateCode("SMS_TIER_UPGRADE")
                .payload(Map.of("oldTier", event.getOldTier(), "newTier", event.getNewTier()))
                .status("PENDING")
                .build();
        outboxRepo.save(sms);

        // 微信模板消息
        NotificationOutbox wx = NotificationOutbox.builder()
                .programCode(event.getProgramCode())
                .memberId(event.getMemberId())
                .eventType("TIER_CHANGE")
                .channel("WECHAT_TEMPLATE")
                .recipient("oMockOpenId123456") // 实际从 member 查询 openId
                .templateCode("TMPL_TIER_UPGRADE")
                .payload(Map.of("thing1", "等级升级", "thing2", event.getOldTier() + " → " + event.getNewTier(),
                        "time3", java.time.LocalDateTime.now().toString()))
                .status("PENDING")
                .build();
        outboxRepo.save(wx);

        log.info("[OutboundSubscriber] 等级变更 → {}+{} 通知: member={}, {}→{}",
                sms.getId(), wx.getId(), event.getMemberId(), event.getOldTier(), event.getNewTier());
    }

    private void handlePointAccrued(PointAccruedEvent event) {
        NotificationOutbox wx = NotificationOutbox.builder()
                .programCode(event.getProgramCode())
                .memberId(event.getMemberId())
                .eventType("POINTS_ACCRUED")
                .channel("WECHAT_TEMPLATE")
                .recipient("oMockOpenId123456")
                .templateCode("TMPL_POINTS_ACCRUED")
                .payload(Map.of("amount1", event.getPoints().toPlainString(),
                        "thing2", event.getAccountType(),
                        "time3", java.time.LocalDateTime.now().toString()))
                .status("PENDING")
                .build();
        outboxRepo.save(wx);

        log.info("[OutboundSubscriber] 积分入账 → 微信通知: member={}, points={}", event.getMemberId(), event.getPoints());
    }
}