package com.loyalty.platform.flow.mapping;

import java.util.*;
import java.util.function.Function;

/**
 * 出站映射执行器 — 将业务实体数据映射到外部 API 请求格式并发送。
 *
 * <p>支持映射类型: PATH (直接路径), EXPRESSION (转换表达式), CONSTANT (常量)。
 * 通过 SPI 接口
 *
 * @see entity_config.md 第7.3节
 */
public class OutboundMappingExecutor {

    private final List<OutboundSender> senders;

    public OutboundMappingExecutor(List<OutboundSender> senders) {
        this.senders = senders != null ? senders : List.of();
    }

    /**
     * 执行出站映射并发送。
     *
     * @param channel      目标渠道
     * @param businessData 源业务实体数据
     * @param mappings     出站映射规则
     * @return 外部 API 响应 (JSON 字符串)
     */
    @SuppressWarnings("unchecked")
    public String execute(String channel, Map<String, Object> businessData,
                          List<InboundMappingGenerator.MappingRule> mappings) {
        // 1. 构建 API 请求实体
        Map<String, Object> requestEntity = new LinkedHashMap<>();

        for (InboundMappingGenerator.MappingRule rule : mappings) {
            String target = rule.getTarget();
            Object value = resolveValue(businessData, rule);
            setNestedValue(requestEntity, target, value);
        }

        // 2. 找到匹配的发送器
        OutboundSender sender = senders.stream()
                .filter(s -> s.supports(channel))
                .findFirst()
                .orElse(null);

        if (sender == null) {
            return "{\"error\": \"No sender found for channel: " + channel + "\"}";
        }

        // 3. 发送请求
        return sender.send(requestEntity);
    }

    /** 根据映射规则解析值 */
    private Object resolveValue(Map<String, Object> source,
                                InboundMappingGenerator.MappingRule rule) {
        switch (rule.getType()) {
            case "PATH":
                return getNestedValue(source, rule.getSource());
            case "EXPRESSION":
                Object val = getNestedValue(source, rule.getSource());
                return applyExpression(val, rule.getExpression());
            case "CONSTANT":
                return rule.getConstant();
            default:
                return null;
        }
    }

    /** 从嵌套 Map 中取值: "a.b.c" → nested.get("a").get("b").get("c") */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> source, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = source;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    /** 设置嵌套值 */
    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> target, String path, Object value) {
        if (path == null || path.isEmpty()) return;
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
        }
        current.put(parts[parts.length - 1], value);
    }

    /** 应用表达式转换 */
    private Object applyExpression(Object value, String expression) {
        if (expression == null || value == null) return value;
        // 支持常见转换: toString, toISOString, parseInt, parseFloat
        return switch (expression) {
            case "toString" -> String.valueOf(value);
            case "parseInt" -> {
                try { yield Integer.parseInt(String.valueOf(value)); }
                catch (NumberFormatException e) { yield value; }
            }
            case "parseFloat" -> {
                try { yield Double.parseDouble(String.valueOf(value)); }
                catch (NumberFormatException e) { yield value; }
            }
            default -> value;
        };
    }

    /** 出站发送器接口 (SPI) */
    public interface OutboundSender {
        boolean supports(String channel);
        String send(Map<String, Object> requestEntity);
    }
}