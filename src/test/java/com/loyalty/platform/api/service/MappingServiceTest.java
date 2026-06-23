package com.loyalty.platform.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.api.dto.MappingRuleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MappingService 单元测试 — 仅测试映射执行逻辑（无需数据库/Spring上下文）。
 *
 * <p>覆盖五种映射类型：PATH, EXPRESSION, CONSTANT, SCRIPT, ARRAY_MAPPING。
 */
@DisplayName("MappingService — 映射执行逻辑")
class MappingServiceTest {

    private MappingService mappingService;

    @BeforeEach
    void setUp() {
        mappingService = new MappingService(null, new ObjectMapper());
    }

    // ======================== PATH 直接映射 ========================

    @Test
    @DisplayName("PATH: 顶层字段直接映射")
    void pathTopLevel() {
        var rules = List.of(
                new MappingRuleDto("tid", "orderId", "PATH"),
                new MappingRuleDto("payment", "totalAmount", "PATH")
        );
        var result = mappingService.testMapping(
                "{\"tid\":\"12345\",\"payment\":\"99.90\"}", rules);

        assertEquals("12345", result.get("orderId"));
        assertEquals("99.90", result.get("totalAmount"));
    }

    @Test
    @DisplayName("PATH: 嵌套路径取值 (a.b.c)")
    void pathNested() {
        var rules = List.of(
                new MappingRuleDto("user.profile.name", "memberName", "PATH")
        );
        var result = mappingService.testMapping(
                "{\"user\":{\"profile\":{\"name\":\"张三\"}}}", rules);

        assertEquals("张三", result.get("memberName"));
    }

    // ======================== EXPRESSION 表达式映射 ========================

    @Test
    @DisplayName("EXPRESSION: parseFloat 字符串→浮点数")
    void expressionParseFloat() {
        var rules = List.of(rule("payment", "totalAmount", "EXPRESSION", "parseFloat"));
        var result = mappingService.testMapping(
                "{\"payment\":\"99.90\"}", rules);

        assertEquals(99.90, result.get("totalAmount"));
    }

    @Test
    @DisplayName("EXPRESSION: toString 数字→字符串")
    void expressionToString() {
        var rules = List.of(rule("totalAmount", "totalAmountStr", "EXPRESSION", "toString"));
        var result = mappingService.testMapping(
                "{\"totalAmount\":100}", rules);

        assertEquals("100", result.get("totalAmountStr"));
    }

    @Test
    @DisplayName("EXPRESSION: toNumber 字符串→数字")
    void expressionToNumber() {
        var rules = List.of(rule("quantity", "quantity", "EXPRESSION", "toNumber"));
        var result = mappingService.testMapping(
                "{\"quantity\":\"5\"}", rules);

        assertEquals(5.0, result.get("quantity"));
    }

    @Test
    @DisplayName("EXPRESSION: parseInt 字符串→整数")
    void expressionParseInt() {
        var rules = List.of(rule("count", "count", "EXPRESSION", "parseInt"));
        var result = mappingService.testMapping(
                "{\"count\":\"42\"}", rules);

        assertEquals(42, result.get("count"));
    }

    // ======================== CONSTANT 常量映射 ========================

    @Test
    @DisplayName("CONSTANT: 常量值始终映射")
    void constantMapping() {
        var rule = new MappingRuleDto("__ignored__", "channel", "CONSTANT");
        rule.setConstant("TMALL");
        var result = mappingService.testMapping("{}", List.of(rule));

        assertEquals("TMALL", result.get("channel"));
    }

    // ======================== SCRIPT 自定义脚本 ========================

    @Test
    @DisplayName("SCRIPT: GraalVM 执行 JS 变换")
    void scriptTransform() {
        // GraalVM polyglot: Java Map bound as 'source' — access via bracket notation
        var rule = new MappingRuleDto("payment", "totalAmountCents", "SCRIPT");
        // Check if GraalVM JS is available first
        boolean jsOk = jsAvailable();
        if (!jsOk) return;

        rule.setScript("Math.round(parseFloat(source.payment) * 100)");
        var result = mappingService.testMapping(
                "{\"payment\":\"99.90\"}", List.of(rule));

        assertNotNull(result.get("totalAmountCents"), "SCRIPT 类型应返回结果");
        assertEquals(9990.0, result.get("totalAmountCents"));
    }

    /** 快速检测 GraalVM JS 引擎是否可用 */
    private static boolean jsAvailable() {
        try (var ctx = org.graalvm.polyglot.Context.create("js")) {
            ctx.eval("js", "1+1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("SCRIPT: 空脚本返回 null")
    void scriptEmptyReturnsNull() {
        if (!jsAvailable()) return;
        var rule = new MappingRuleDto("x", "y", "SCRIPT");
        rule.setScript("");
        var result = mappingService.testMapping("{\"x\":1}", List.of(rule));

        assertNull(result.get("y"));
    }

    // ======================== ARRAY_MAPPING 数组映射 ========================

    @Test
    @DisplayName("ARRAY_MAPPING: 子元素字段映射")
    void arrayMappingBasic() {
        var parent = new MappingRuleDto("orders", "items", "ARRAY_MAPPING");
        parent.setParentArray("orders");
        parent.setItemMapping(List.of(
                new MappingRuleDto("oid", "orderItemId", "PATH"),
                rule("num", "quantity", "EXPRESSION", "toNumber"),
                rule("price", "unitPrice", "EXPRESSION", "parseFloat")
        ));
        var result = mappingService.testMapping(
                "{\"orders\":[{\"oid\":\"A1\",\"num\":\"2\",\"price\":\"49.90\"},{\"oid\":\"A2\",\"num\":\"1\",\"price\":\"99.00\"}]}",
                List.of(parent));

        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items);
        assertEquals(2, items.size());

        assertEquals("A1", items.get(0).get("orderItemId"));
        assertEquals(2.0, items.get(0).get("quantity"));
        assertEquals(49.90, items.get(0).get("unitPrice"));

        assertEquals("A2", items.get(1).get("orderItemId"));
        assertEquals(1.0, items.get(1).get("quantity"));
        assertEquals(99.00, items.get(1).get("unitPrice"));
    }

    @Test
    @DisplayName("ARRAY_MAPPING: parentArray 为空返回 null")
    void arrayMappingEmptyParentArray() {
        var parent = new MappingRuleDto("x", "y", "ARRAY_MAPPING");
        parent.setParentArray(null);
        var result = mappingService.testMapping("{\"x\":[1]}", List.of(parent));

        assertNull(result.get("y"));
    }

    // ======================== 入站/出站完整映射 ========================

    @Test
    @DisplayName("入站映射: 天猫订单 → 业务订单 (orderCreate)")
    void inboundOrderCreate() {
        var rules = List.of(
                new MappingRuleDto("tid", "orderId", "PATH"),
                rule("payment", "totalAmount", "EXPRESSION", "parseFloat"),
                rule("pay_time", "paidAt", "EXPRESSION", "toISOString")
        );
        var result = mappingService.testMapping(
                "{\"tid\":\"T20240115001\",\"payment\":\"259.00\",\"pay_time\":\"2024-01-15T10:30:00Z\"}",
                rules);

        assertEquals("T20240115001", result.get("orderId"));
        assertEquals(259.00, result.get("totalAmount"));
        assertNotNull(result.get("paidAt"));
    }

    @Test
    @DisplayName("出站映射: 业务订单 → 天猫请求 (orderCreate)")
    void outboundOrderCreate() {
        var rules = List.of(
                new MappingRuleDto("orderId", "out_trade_no", "PATH"),
                rule("totalAmount", "total_amount", "EXPRESSION", "toString")
        );
        var result = mappingService.testMapping(
                "{\"orderId\":\"ORD-20001\",\"totalAmount\":399.00}",
                rules);

        assertEquals("ORD-20001", result.get("out_trade_no"));
        assertEquals("399.0", result.get("total_amount"));
    }

    // ======================== 异常场景 ========================

    @Test
    @DisplayName("无效 JSON 抛出 RuntimeException")
    void invalidJsonThrows() {
        var rules = List.of(new MappingRuleDto("x", "y", "PATH"));
        assertThrows(RuntimeException.class,
                () -> mappingService.testMapping("not-json", rules));
    }

    @Test
    @DisplayName("PATH: 不存在字段返回 null")
    void pathMissingFieldReturnsNull() {
        var rules = List.of(new MappingRuleDto("nonexistent", "target", "PATH"));
        var result = mappingService.testMapping("{\"a\":1}", rules);
        assertNull(result.get("target"));
    }

    // ======================== 辅助 ========================

    private static MappingRuleDto rule(String source, String target, String type, String expression) {
        MappingRuleDto r = new MappingRuleDto(source, target, type);
        r.setExpression(expression);
        return r;
    }
}
