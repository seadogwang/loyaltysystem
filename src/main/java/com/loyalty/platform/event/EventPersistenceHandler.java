package com.loyalty.platform.event;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.event.BaseDomainEvent;
import com.loyalty.platform.common.event.DomainEventHandler;
import com.loyalty.platform.domain.entity.EventFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 事件持久化处理器 — 订阅 loyalty-events 主题，将 EventFact 写入 transaction_event 表。
 *
 * <p>通过 SchemaMappingResolver 解析 schema_type 和 schema_version，
 * 确保 transaction_event.schema_version 列正确填充。
 */
@Component
public class EventPersistenceHandler implements DomainEventHandler<BaseDomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(EventPersistenceHandler.class);

    private final SchemaMappingResolver schemaResolver;
    private final CascadePersistenceService cascadeService;

    public EventPersistenceHandler(SchemaMappingResolver schemaResolver,
                                    CascadePersistenceService cascadeService) {
        this.schemaResolver = schemaResolver;
        this.cascadeService = cascadeService;
    }

    @Override
    public String getTopic() {
        return "loyalty-events";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<BaseDomainEvent> getEventType() {
        return BaseDomainEvent.class;
    }

    @Override
    public void handle(BaseDomainEvent event) {
        if (!(event instanceof EventFact fact)) {
            return;
        }

        TenantContext.set(fact.getProgramCode());
        try {
            String eventType = fact.getEventType();
            String schemaType = schemaResolver.resolveSchemaType(eventType);
            String schemaVersion = schemaResolver.resolveSchemaVersion(fact.getProgramCode(), schemaType);

            log.debug("[EventPersistence] 持久化: eventId={}, type={}, schemaType={}, schemaVersion={}",
                    event.getEventId(), eventType, schemaType, schemaVersion);

            cascadeService.persistCascade(
                    fact.getProgramCode(),
                    eventType,
                    fact.getPayload(),
                    schemaVersion,
                    fact.getChannel(),
                    fact.getEventTime(),
                    fact.getMemberId() != null ? Long.parseLong(fact.getMemberId()) : null,
                    event.getEventId(),
                    fact.getIdempotentKey()
            );

        } catch (Exception e) {
            log.error("[EventPersistence] 持久化失败: eventId={}", event.getEventId(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
