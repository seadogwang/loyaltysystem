package com.loyalty.platform.flow.components;

import com.loyalty.platform.domain.entity.EventFact;
import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;

import java.time.Instant;
import java.util.Map;

/**
 * 事实构建组件 — 构建 EventFact 对象供规则引擎使用。
 *
 * <p>从 EventContext 中提取 programCode、channel、memberId、eventType 等信息，
 * 组装标准 EventFact，设置到 ctx.eventFact。
 */
@LiteflowComponent("factBuilderCmp")
public class FactBuilderComponent extends BaseLiteflowComponent {

    @Override
    protected void doProcess(EventContext ctx) throws Exception {
        Map<String, Object> payload = ctx.getStandardizedPayload();
        if (payload == null) payload = Map.of();

        String memberId = ctx.getMemberFact() != null
                ? String.valueOf(ctx.getMemberFact().getMemberId()) : "unknown";
        String eventType = extractEventType(payload);
        String channel = ctx.getChannel() != null ? ctx.getChannel() : "UNKNOWN";

        // 提取业务事件时间
        Instant eventTime = extractEventTime(payload);

        EventFact fact = new EventFact(
                ctx.getProgramCode(),
                eventType,
                memberId,
                channel,
                eventTime,
                ctx.getIdempotencyKey(),
                null,
                payload
        );

        ctx.setEventFact(fact);
        log.debug("[FactBuilder] eventFact 构建完成: type={}, memberId={}", eventType, memberId);
    }

    private String extractEventType(Map<String, Object> payload) {
        if (payload.containsKey("eventType")) return String.valueOf(payload.get("eventType"));
        if (payload.containsKey("event_type")) return String.valueOf(payload.get("event_type"));
        if (payload.containsKey("action")) return String.valueOf(payload.get("action"));
        return "CUSTOM";
    }

    private Instant extractEventTime(Map<String, Object> payload) {
        Object ts = payload.get("timestamp");
        if (ts instanceof Number n) {
            if (n.longValue() > 1_000_000_000_000L) return Instant.ofEpochMilli(n.longValue());
            return Instant.ofEpochSecond(n.longValue());
        }
        return Instant.now();
    }
}