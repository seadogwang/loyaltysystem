package com.loyalty.platform.api.service;

import com.loyalty.platform.event.SchemaMappingResolver;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 事件数据查询服务 —— 按 schema 类型查询 transaction_event + custom_entity_data。
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EntityManager em;
    private final SchemaMappingResolver schemaResolver;

    public EventService(EntityManager em, SchemaMappingResolver schemaResolver) {
        this.em = em;
        this.schemaResolver = schemaResolver;
    }

    // ==================== 事件类型映射 ====================

    /** ORDER 对应的事件类型 */
    private static final List<String> ORDER_EVENT_TYPES =
            List.of("ORDER_PAID", "ORDER_REFUND_FULL", "ORDER_REFUND_PARTIAL");

    /** BEHAVIOR 对应的事件类型 */
    private static final List<String> BEHAVIOR_EVENT_TYPES =
            List.of("CHECK_IN", "SHARE", "REGISTER", "SIGN_IN");

    // ==================== 订单查询 (ORDER schema) ====================

    /**
     * 查询订单事件列表。
     */
    public Map<String, Object> queryEvents(String programCode, String schemaType,
                                            Long memberId, String channel, String tradeStatus,
                                            int page, int size) {
        List<String> eventTypes = getOrderEventTypes();

        StringBuilder jpql = new StringBuilder(
                "FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc AND t.eventType IN :eventTypes");
        StringBuilder cntJpql = new StringBuilder(
                "SELECT COUNT(t) FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc AND t.eventType IN :eventTypes");

        List<Object> params = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        if (memberId != null) {
            jpql.append(" AND t.memberId=:memberId");
            cntJpql.append(" AND t.memberId=:memberId");
            paramNames.add("memberId");
            params.add(memberId);
        }
        if (channel != null && !channel.isBlank()) {
            jpql.append(" AND t.channel=:channel");
            cntJpql.append(" AND t.channel=:channel");
            paramNames.add("channel");
            params.add(channel);
        }
        if (tradeStatus != null && !tradeStatus.isBlank()) {
            jpql.append(" AND t.tradeStatus=:tradeStatus");
            cntJpql.append(" AND t.tradeStatus=:tradeStatus");
            paramNames.add("tradeStatus");
            params.add(tradeStatus);
        }

        jpql.append(" ORDER BY t.eventTime DESC");

        var q = em.createQuery(jpql.toString(), com.loyalty.platform.domain.entity.TransactionEvent.class);
        var cq = em.createQuery(cntJpql.toString(), Long.class);

        q.setParameter("pc", programCode).setParameter("eventTypes", eventTypes);
        cq.setParameter("pc", programCode).setParameter("eventTypes", eventTypes);

        for (int i = 0; i < paramNames.size(); i++) {
            q.setParameter(paramNames.get(i), params.get(i));
            cq.setParameter(paramNames.get(i), params.get(i));
        }

        long total = cq.getSingleResult();
        var list = q.setFirstResult(page * size).setMaxResults(size).getResultList();

        String schemaVersion = schemaResolver.resolveSchemaVersion(programCode, schemaType);

        return buildResult(list, total, page, size, schemaType, schemaVersion);
    }

    /**
     * 订单详情 —— 含 OrderItem 明细。
     */
    public Map<String, Object> getOrderDetail(String programCode, String eventId) {
        List<com.loyalty.platform.domain.entity.TransactionEvent> events = em.createQuery(
                "FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc AND t.eventId=:eid",
                com.loyalty.platform.domain.entity.TransactionEvent.class)
                .setParameter("pc", programCode)
                .setParameter("eid", eventId)
                .getResultList();

        if (events.isEmpty()) return null;

        var t = events.get(0);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("event_id", t.getEventId());
        detail.put("event_type", t.getEventType());
        detail.put("member_id", t.getMemberId());
        detail.put("channel", t.getChannel());
        detail.put("event_time", t.getEventTime());
        detail.put("trade_time", t.getTradeTime());
        detail.put("pay_time", t.getPayTime());
        detail.put("order_amount", t.getOrderAmount());
        detail.put("trade_status", t.getTradeStatus());
        detail.put("schema_version", t.getSchemaVersion());
        detail.put("ext_attributes", t.getExtAttributes());

        // 查询 OrderItem 明细
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawItems = em.createNativeQuery(
                    "SELECT attributes FROM custom_entity_data "
                    + "WHERE program_code=:pc AND parent_event_id=:eid "
                    + "ORDER BY created_at")
                    .setParameter("pc", programCode)
                    .setParameter("eid", eventId)
                    .getResultList();
            for (Object row : rawItems) {
                try {
                    String json = String.valueOf(row);
                    if (!json.isEmpty() && !json.equals("null")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(json, Map.class);
                        items.add(parsed);
                    }
                } catch (Exception e) {
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("raw", String.valueOf(row));
                    items.add(raw);
                }
            }
        } catch (Exception e) {
            log.warn("[EventService] 查询 OrderItem 明细失败: eventId={}", eventId, e);
        }

        detail.put("items", items);
        return detail;
    }

    // ==================== 行为查询 (BEHAVIOR schema) ====================

    public Map<String, Object> queryBehaviorEvents(String programCode, Long memberId,
                                                     String eventType, String channel,
                                                     int page, int size) {
        List<String> eventTypes = BEHAVIOR_EVENT_TYPES;

        StringBuilder jpql = new StringBuilder(
                "FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc AND t.eventType IN :eventTypes");
        StringBuilder cntJpql = new StringBuilder(
                "SELECT COUNT(t) FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc AND t.eventType IN :eventTypes");

        List<Object> params = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        if (memberId != null) {
            jpql.append(" AND t.memberId=:memberId");
            cntJpql.append(" AND t.memberId=:memberId");
            paramNames.add("memberId");
            params.add(memberId);
        }
        if (eventType != null && !eventType.isBlank()) {
            jpql.append(" AND t.eventType=:eventType");
            cntJpql.append(" AND t.eventType=:eventType");
            paramNames.add("eventType");
            params.add(eventType);
        }
        if (channel != null && !channel.isBlank()) {
            jpql.append(" AND t.channel=:channel");
            cntJpql.append(" AND t.channel=:channel");
            paramNames.add("channel");
            params.add(channel);
        }

        jpql.append(" ORDER BY t.eventTime DESC");

        var q = em.createQuery(jpql.toString(), com.loyalty.platform.domain.entity.TransactionEvent.class);
        var cq = em.createQuery(cntJpql.toString(), Long.class);

        q.setParameter("pc", programCode).setParameter("eventTypes", eventTypes);
        cq.setParameter("pc", programCode).setParameter("eventTypes", eventTypes);

        for (int i = 0; i < paramNames.size(); i++) {
            q.setParameter(paramNames.get(i), params.get(i));
            cq.setParameter(paramNames.get(i), params.get(i));
        }

        long total = cq.getSingleResult();
        var list = q.setFirstResult(page * size).setMaxResults(size).getResultList();

        String schemaVersion = schemaResolver.resolveSchemaVersion(programCode, "BEHAVIOR");

        return buildResult(list, total, page, size, "BEHAVIOR", schemaVersion);
    }

    // ==================== 全部交易事件 (TRANSACTION schema) ====================

    public Map<String, Object> queryAllTransactions(String programCode, Long memberId,
                                                      String eventType, String channel,
                                                      String dateFrom, String dateTo,
                                                      int page, int size) {
        StringBuilder jpql = new StringBuilder(
                "FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc");
        StringBuilder cntJpql = new StringBuilder(
                "SELECT COUNT(t) FROM com.loyalty.platform.domain.entity.TransactionEvent t "
                + "WHERE t.programCode=:pc");

        List<Object> params = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        if (memberId != null) {
            jpql.append(" AND t.memberId=:memberId");
            cntJpql.append(" AND t.memberId=:memberId");
            paramNames.add("memberId");
            params.add(memberId);
        }
        if (eventType != null && !eventType.isBlank()) {
            jpql.append(" AND t.eventType=:eventType");
            cntJpql.append(" AND t.eventType=:eventType");
            paramNames.add("eventType");
            params.add(eventType);
        }
        if (channel != null && !channel.isBlank()) {
            jpql.append(" AND t.channel=:channel");
            cntJpql.append(" AND t.channel=:channel");
            paramNames.add("channel");
            params.add(channel);
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            jpql.append(" AND t.eventTime >= :dateFrom");
            cntJpql.append(" AND t.eventTime >= :dateFrom");
            paramNames.add("dateFrom");
            params.add(LocalDateTime.parse(dateFrom));
        }
        if (dateTo != null && !dateTo.isBlank()) {
            jpql.append(" AND t.eventTime <= :dateTo");
            cntJpql.append(" AND t.eventTime <= :dateTo");
            paramNames.add("dateTo");
            params.add(LocalDateTime.parse(dateTo));
        }

        jpql.append(" ORDER BY t.eventTime DESC");

        var q = em.createQuery(jpql.toString(), com.loyalty.platform.domain.entity.TransactionEvent.class);
        var cq = em.createQuery(cntJpql.toString(), Long.class);

        q.setParameter("pc", programCode);
        cq.setParameter("pc", programCode);

        for (int i = 0; i < paramNames.size(); i++) {
            q.setParameter(paramNames.get(i), params.get(i));
            cq.setParameter(paramNames.get(i), params.get(i));
        }

        long total = cq.getSingleResult();
        var list = q.setFirstResult(page * size).setMaxResults(size).getResultList();

        String schemaVersion = schemaResolver.resolveSchemaVersion(programCode, "TRANSACTION");

        return buildResult(list, total, page, size, "TRANSACTION", schemaVersion);
    }

    // ==================== 通用查询 ====================

    public Map<String, Object> queryBySchemaType(String programCode, String schemaType,
                                                   Long memberId, int page, int size) {
        switch (schemaType) {
            case "ORDER":
                return queryEvents(programCode, schemaType, memberId, null, null, page, size);
            case "BEHAVIOR":
                return queryBehaviorEvents(programCode, memberId, null, null, page, size);
            case "TRANSACTION":
                return queryAllTransactions(programCode, memberId, null, null, null, null, page, size);
            case "MEMBER":
                return queryMemberSchemaData(programCode, memberId, page, size);
            case "ORDERITEM":
                return queryOrderItemData(programCode, null, page, size);
            default:
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "UNKNOWN_SCHEMA_TYPE");
                error.put("supported", List.of("ORDER", "BEHAVIOR", "MEMBER", "TRANSACTION", "OrderItem"));
                return error;
        }
    }

    // ==================== 辅助方法 ====================

    private List<String> getOrderEventTypes() {
        return ORDER_EVENT_TYPES;
    }

    private Map<String, Object> buildResult(List<com.loyalty.platform.domain.entity.TransactionEvent> events,
                                              long total, int page, int size,
                                              String schemaType, String schemaVersion) {
        List<Map<String, Object>> data = events.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("event_id", t.getEventId());
            m.put("event_type", t.getEventType());
            m.put("member_id", t.getMemberId());
            m.put("channel", t.getChannel());
            m.put("event_time", t.getEventTime());
            m.put("trade_time", t.getTradeTime());
            m.put("pay_time", t.getPayTime());
            m.put("order_amount", t.getOrderAmount());
            m.put("trade_status", t.getTradeStatus());
            m.put("schema_version", t.getSchemaVersion());
            m.put("ext_attributes", t.getExtAttributes());
            m.put("created_at", t.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_type", schemaType);
        result.put("schema_version", schemaVersion);
        result.put("data", data);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private Map<String, Object> queryMemberSchemaData(String programCode, Long memberId, int page, int size) {
        // MEMBER 数据从 member 表查询
        String jpql = "FROM com.loyalty.platform.domain.entity.Member m WHERE m.programCode=:pc";
        String cntJpql = "SELECT COUNT(m) FROM com.loyalty.platform.domain.entity.Member m WHERE m.programCode=:pc";

        var q = em.createQuery(jpql, com.loyalty.platform.domain.entity.Member.class);
        var cq = em.createQuery(cntJpql, Long.class);
        q.setParameter("pc", programCode);
        cq.setParameter("pc", programCode);

        if (memberId != null) {
            q = em.createQuery(jpql + " AND m.memberId=:mid", com.loyalty.platform.domain.entity.Member.class);
            cq = em.createQuery(cntJpql + " AND m.memberId=:mid", Long.class);
            q.setParameter("pc", programCode).setParameter("mid", memberId);
            cq.setParameter("pc", programCode).setParameter("mid", memberId);
        }

        q.setFirstResult(page * size).setMaxResults(size);
        long total = cq.getSingleResult();

        String schemaVersion = schemaResolver.resolveSchemaVersion(programCode, "MEMBER");

        List<Map<String, Object>> data = q.getResultList().stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("member_id", m.getMemberId());
            map.put("program_code", m.getProgramCode());
            map.put("name", m.getName());
            map.put("ext_attributes", m.getExtAttributes());
            map.put("schema_version", m.getSchemaVersion());
            map.put("created_at", m.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_type", "MEMBER");
        result.put("schema_version", schemaVersion);
        result.put("data", data);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private Map<String, Object> queryOrderItemData(String programCode, String parentEventId, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, entity_code, parent_event_id, attributes, created_at "
                + "FROM custom_entity_data WHERE program_code=:pc");
        StringBuilder cntSql = new StringBuilder(
                "SELECT COUNT(*) FROM custom_entity_data WHERE program_code=:pc");

        jakarta.persistence.Query q;
        jakarta.persistence.Query cq;

        if (parentEventId != null) {
            sql.append(" AND parent_event_id=:parentId");
            cntSql.append(" AND parent_event_id=:parentId");
        }
        sql.append(" ORDER BY created_at DESC");

        q = em.createNativeQuery(sql.toString());
        cq = em.createNativeQuery(cntSql.toString());
        q.setParameter("pc", programCode);
        cq.setParameter("pc", programCode);

        if (parentEventId != null) {
            q.setParameter("parentId", parentEventId);
            cq.setParameter("parentId", parentEventId);
        }

        q.setFirstResult(page * size).setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        long total = ((Number) cq.getSingleResult()).longValue();

        String schemaVersion = schemaResolver.resolveSchemaVersion(programCode, "OrderItem");

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("entity_code", row[1]);
            m.put("parent_event_id", row[2]);
            m.put("attributes", row[3]);
            m.put("created_at", row[4]);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_type", "OrderItem");
        result.put("schema_version", schemaVersion);
        result.put("data", data);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
