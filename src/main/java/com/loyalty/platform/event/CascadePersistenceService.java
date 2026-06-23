package com.loyalty.platform.event;

import com.loyalty.platform.common.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 1:N 级联持久化服务。
 *
 * <p>将映射引擎清洗后的标准 JSON 拆分为主表 (transaction_event) +
 * 子表 (custom_entity_data 明细)，在一个强事务中落盘。
 *
 * <p>事件类型处理：
 * <ul>
 *   <li>ORDER_* → 写 transaction_event + custom_entity_data (订单明细)</li>
 *   <li>BEHAVIOR → 只写 transaction_event</li>
 *   <li>MEMBER → 更新 member.ext_attributes</li>
 * </ul>
 *
 * <p><b>字段提取</b>：从 payload 提取到 transaction_event 主表列的字段名，
 * 按 eventType 前缀从 {@link #EVENT_COLUMN_KEYS} 映射读取，而非硬编码。
 * 扩展新事件类型只需在 EVENT_COLUMN_KEYS 中添加映射条目。
 */
@Service
public class CascadePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(CascadePersistenceService.class);

    /** 事件类型前缀 → 需提取到主表列的字段名集合 */
    private static final Map<String, Set<String>> EVENT_COLUMN_KEYS = new LinkedHashMap<>();
    static {
        EVENT_COLUMN_KEYS.put("ORDER", Set.of("order_amount", "trade_status", "pay_time"));
    }

    /** 所有事件类型的公共元数据字段 — 从 extAttributes 中移除 */
    private static final Set<String> COMMON_METADATA_KEYS = Set.of(
            "trade_time", "event_time", "member_id", "eventType", "event_type"
    );

    @PersistenceContext private EntityManager em;

    /**
     * 级联持久化事件。
     *
     * @param programCode   租户代码
     * @param eventType     事件类型
     * @param payload       映射后的标准 JSON payload
     * @param schemaVersion schema 版本标签（如 "order_schema:v1"）
     * @param channel       渠道标识
     * @param eventTime     事件时间
     * @param memberId      会员 ID（可为 null）
     * @param eventId       业务事件 ID（如为空则自动生成）
     * @param idempotencyKey 幂等键
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistCascade(String programCode, String eventType, Map<String, Object> payload,
                                String schemaVersion, String channel, Instant eventTime,
                                Long memberId, String eventId, String idempotencyKey) {
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }

        LocalDateTime tradeTime = eventTime != null
                ? LocalDateTime.ofInstant(eventTime, ZoneId.systemDefault())
                : LocalDateTime.now();

        // 提取 transaction_event 共用列 — 按事件类型前缀从映射表读取
        String eventTypePrefix = eventType.split("_")[0]; // ORDER_PAID → ORDER
        Set<String> columnKeys = EVENT_COLUMN_KEYS.getOrDefault(eventTypePrefix, Set.of());

        String orderAmount = columnKeys.contains("order_amount") ? extractString(payload, "order_amount") : null;
        String tradeStatus = columnKeys.contains("trade_status") ? extractString(payload, "trade_status") : null;
        String payTimeStr = columnKeys.contains("pay_time") ? extractString(payload, "pay_time") : null;
        LocalDateTime payTime = payTimeStr != null ? parseLocalDateTime(payTimeStr) : null;

        // 清理 payload 中已提取到主表列的字段 — 从映射 + 公共元数据键列表构建移除集
        Map<String, Object> extAttributes = new LinkedHashMap<>(payload);
        Set<String> keysToRemove = new LinkedHashSet<>();
        keysToRemove.addAll(columnKeys);
        keysToRemove.addAll(COMMON_METADATA_KEYS);
        keysToRemove.forEach(extAttributes::remove);

        // Step 1: 写主表 transaction_event
        em.createNativeQuery(
                "INSERT INTO transaction_event (event_id, program_code, member_id, event_type, "
                        + "event_time, channel, source_event_id, idempotency_key, "
                        + "ext_attributes, processing_status, schema_version, "
                        + "trade_time, pay_time, order_amount, trade_status, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .setParameter(1, eventId)
                .setParameter(2, programCode)
                .setParameter(3, memberId)
                .setParameter(4, eventType)
                .setParameter(5, tradeTime)
                .setParameter(6, channel)
                .setParameter(7, eventId)
                .setParameter(8, idempotencyKey != null ? idempotencyKey : eventId)
                .setParameter(9, toJson(extAttributes))
                .setParameter(10, "SUCCEEDED")
                .setParameter(11, schemaVersion)
                .setParameter(12, tradeTime)
                .setParameter(13, payTime)
                .setParameter(14, parseBigDecimal(orderAmount))
                .setParameter(15, tradeStatus)
                .setParameter(16, LocalDateTime.now())
                .executeUpdate();

        int lineCount = 0;

        // Step 2: 拆解 1:N 明细（ORDER 的 items 数组 → custom_entity_data）
        Object linesObj = payload.get("items");
        if (linesObj == null) {
            linesObj = payload.get("lines");
        }
        if (linesObj instanceof List<?> lines) {
            for (Object line : lines) {
                if (line instanceof Map<?, ?> lineMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) lineMap;
                    persistLine(programCode, eventId, channel, data, ++lineCount);
                }
            }
        }

        log.info("[CascadePersistence] 持久化完成: eventId={}, type={}, schemaVer={}, lines={}",
                eventId, eventType, schemaVersion, lineCount);
    }

    private void persistLine(String programCode, String parentEventId, String channel,
                              Map<String, Object> data, int order) {
        em.createNativeQuery(
                "INSERT INTO custom_entity_data (program_code, entity_code, parent_event_id, "
                        + "attributes, created_at) VALUES (?,?,?,?::jsonb,?)")
                .setParameter(1, programCode)
                .setParameter(2, "ORDER_LINE_" + channel)
                .setParameter(3, parentEventId)
                .setParameter(4, toJson(data))
                .setParameter(5, LocalDateTime.now())
                .executeUpdate();
    }

    // ==================== 辅助方法 ====================

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private java.math.BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try { return new java.math.BigDecimal(val); } catch (Exception e) { return null; }
    }

    private LocalDateTime parseLocalDateTime(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return java.time.LocalDateTime.parse(val,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {}
        try {
            return java.time.Instant.parse(val).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {}
        return null;
    }

    private String toJson(Object obj) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}