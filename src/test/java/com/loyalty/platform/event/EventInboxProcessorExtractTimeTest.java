package com.loyalty.platform.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EventInboxProcessor extractEventTime() 单元测试。
 * 通过反射测试私有方法的时间解析逻辑，覆盖所有时间格式。
 */
@DisplayName("EventInboxProcessor — 事件时间提取")
class EventInboxProcessorExtractTimeTest {

    private static Instant invokeExtractEventTime(Map<String, Object> payload) throws Exception {
        EventInboxProcessor processor = new EventInboxProcessor(null, null, null, null, null);
        Method method = EventInboxProcessor.class.getDeclaredMethod("extractEventTime", Map.class);
        method.setAccessible(true);
        return (Instant) method.invoke(processor, payload);
    }

    @Test
    @DisplayName("提取顶层 pay_time (天猫/京东回调)")
    void shouldExtractPayTime() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pay_time", "2025-06-01 12:30:00");

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        // 北京时间 2025-06-01 12:30:00 → UTC
        assertEquals("2025-06-01T04:30:00Z", result.toString());
    }

    @Test
    @DisplayName("提取顶层 event_time")
    void shouldExtractEventTime() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_time", "2025-06-01T12:30:00Z");

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        assertEquals("2025-06-01T12:30:00Z", result.toString());
    }

    @Test
    @DisplayName("提取嵌套 trade.pay_time (天猫会员通)")
    void shouldExtractNestedTradePayTime() throws Exception {
        Map<String, Object> trade = new HashMap<>();
        trade.put("pay_time", "2025-06-01 12:30:00");

        Map<String, Object> payload = new HashMap<>();
        payload.put("trade", trade);

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        assertEquals("2025-06-01T04:30:00Z", result.toString());
    }

    @Test
    @DisplayName("提取 Unix 毫秒时间戳")
    void shouldExtractUnixMillisTimestamp() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        long ts = Instant.parse("2025-06-01T12:00:00Z").toEpochMilli();
        payload.put("timestamp", ts);

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        assertEquals("2025-06-01T12:00:00Z", result.toString());
    }

    @Test
    @DisplayName("提取 Unix 秒时间戳")
    void shouldExtractUnixSecondsTimestamp() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        long ts = Instant.parse("2025-06-01T12:00:00Z").getEpochSecond();
        payload.put("timestamp", ts);

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        assertEquals("2025-06-01T12:00:00Z", result.toString());
    }

    @Test
    @DisplayName("提取 ISO-8601 格式")
    void shouldExtractIso8601() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pay_time", "2025-06-01T12:30:00Z");

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        assertEquals("2025-06-01T12:30:00Z", result.toString());
    }

    @Test
    @DisplayName("无有效时间时降级为当前时间")
    void shouldFallbackToNowOnInvalidTime() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_time", "not-a-valid-time");

        Instant result = invokeExtractEventTime(payload);
        assertNotNull(result);
        // 应降级为当前时间，误差应在 2 秒内
        long diff = Math.abs(Instant.now().toEpochMilli() - result.toEpochMilli());
        assertTrue(diff < 2000, "降级时间应在 2 秒内，实际差 " + diff + "ms");
    }

    @Test
    @DisplayName("空 payload 降级为当前时间")
    void shouldFallbackToNowWhenPayloadIsNull() throws Exception {
        Instant result = invokeExtractEventTime(null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("优先级: pay_time > event_time > trade.pay_time > timestamp")
    void shouldFollowPriorityOrder() throws Exception {
        Map<String, Object> trade = new HashMap<>();
        trade.put("pay_time", "2025-01-01 00:00:00");

        Map<String, Object> payload = new HashMap<>();
        payload.put("pay_time", "2025-06-01 12:30:00");        // 优先级最高
        payload.put("event_time", "2025-01-01T00:00:00Z");     // 第二
        payload.put("trade", trade);                            // 第三
        payload.put("timestamp", 1717200000);                   // 第四

        Instant result = invokeExtractEventTime(payload);
        assertEquals("2025-06-01T04:30:00Z", result.toString()); // 取 pay_time
    }
}