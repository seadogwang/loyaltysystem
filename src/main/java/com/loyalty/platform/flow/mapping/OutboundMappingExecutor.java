package com.loyalty.platform.flow.mapping;

import com.loyalty.platform.domain.enums.MappingMode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 出站映射执行器 — 将业务实体数据映射到外部 API 请求格式并发送。
 *
 * <p>支持映射类型:
 * <ul>
 *   <li>{@code PATH} — 直接路径映射 (a.b.c 嵌套路径)
 *   <li>{@code EXPRESSION} — 转换表达式 (toString, parseFloat, toISOString 等)
 *   <li>{@code CONSTANT} — 常量映射
 *   <li>{@code SCRIPT} — GraalVM JavaScript 自定义脚本
 *   <li>{@code ARRAY_MAPPING} — 嵌套数组映射
 * </ul>
 *
 * <p>映射模式 {@link MappingMode}: {@code VISUAL} 走规则列表, {@code SCRIPT} 走自定义脚本。
 *
 * @see entity_config.md 第7.3节
 */
@Component
public class OutboundMappingExecutor {

    private static final Logger log = LoggerFactory.getLogger(OutboundMappingExecutor.class);

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
     * @param mode         映射模式 (VISUAL 或 SCRIPT)
     * @return 外部 API 响应 (JSON 字符串)
     */
    public String execute(String channel, Map<String, Object> businessData,
                          List<InboundMappingGenerator.MappingRule> mappings,
                          MappingMode mode) {
        // 1. 根据模式构建 API 请求实体
        Map<String, Object> requestEntity;

        if (mode == MappingMode.SCRIPT) {
            // SCRIPT 模式: 执行第一条映射中定义的脚本
            requestEntity = executeScriptMode(businessData, mappings);
        } else {
            // VISUAL 模式: 逐条执行映射规则
            requestEntity = new LinkedHashMap<>();
            for (InboundMappingGenerator.MappingRule rule : mappings) {
                Object value = resolveValue(businessData, rule);
                setNestedValue(requestEntity, rule.getTarget(), value);
            }
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

    /**
     * 兼容旧版本调用 (默认 VISUAL 模式)。
     */
    public String execute(String channel, Map<String, Object> businessData,
                          List<InboundMappingGenerator.MappingRule> mappings) {
        return execute(channel, businessData, mappings, MappingMode.VISUAL);
    }

    /** SCRIPT 模式: 执行自定义脚本 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeScriptMode(Map<String, Object> businessData,
                                                   List<InboundMappingGenerator.MappingRule> mappings) {
        // 取第一个 SCRIPT 类型的映射作为主脚本
        String script = mappings.stream()
                .filter(r -> "SCRIPT".equals(r.getType()))
                .map(InboundMappingGenerator.MappingRule::getScript)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);

        if (script == null) {
            return new LinkedHashMap<>();
        }

        try (Context ctx = Context.create("js")) {
            ctx.getBindings("js").putMember("businessData", businessData);
            Value result = ctx.eval("js", script);
            if (result.hasMembers()) {
                return (Map<String, Object>) result.as(Map.class);
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            log.error("[OutboundMappingExecutor] SCRIPT 执行失败: {}", e.getMessage(), e);
            return new LinkedHashMap<>();
        }
    }

    /** 根据映射规则解析值 */
    @SuppressWarnings("unchecked")
    private Object resolveValue(Map<String, Object> source,
                                InboundMappingGenerator.MappingRule rule) {
        return switch (rule.getType()) {
            case "PATH" -> getNestedValue(source, rule.getSource());
            case "EXPRESSION" -> {
                Object val = getNestedValue(source, rule.getSource());
                yield applyExpression(val, rule.getExpression());
            }
            case "CONSTANT" -> rule.getConstant();
            case "SCRIPT" -> executeInlineScript(source, rule.getScript());
            case "ARRAY_MAPPING" -> resolveArrayMapping(source, rule);
            default -> null;
        };
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
        return switch (expression) {
            case "toString" -> String.valueOf(value);
            case "toISOString" -> {
                if (value instanceof Date) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    yield sdf.format((Date) value);
                }
                // 如果已经是字符串，原样返回
                yield String.valueOf(value);
            }
            case "parseInt" -> {
                try {
                    yield Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "parseFloat" -> {
                try {
                    yield Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "formatDate" -> {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                if (value instanceof Date) {
                    yield sdf.format((Date) value);
                }
                yield String.valueOf(value);
            }
            case "concat" -> String.valueOf(value);
            case "default" -> String.valueOf(value);
            case "toNumber" -> {
                try {
                    yield Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            default -> value;
        };
    }

    /** 执行内联 SCRIPT (GraalVM) */
    @SuppressWarnings("unchecked")
    private Object executeInlineScript(Map<String, Object> source, String script) {
        if (script == null || script.isBlank()) return null;
        try (Context ctx = Context.create("js")) {
            ctx.getBindings("js").putMember("source", source);
            Value result = ctx.eval("js", script);
            if (result.isNull()) return null;
            if (result.isString()) return result.asString();
            if (result.isNumber()) return result.asDouble();
            if (result.isBoolean()) return result.asBoolean();
            return result.asString();
        } catch (Exception e) {
            log.warn("[OutboundMappingExecutor] SCRIPT 执行失败: {}", e.getMessage());
            return null;
        }
    }

    /** 处理 ARRAY_MAPPING 类型 — 遍历父数组并对每个元素应用子映射 */
    @SuppressWarnings("unchecked")
    private Object resolveArrayMapping(Map<String, Object> source,
                                       InboundMappingGenerator.MappingRule rule) {
        String parentArray = rule.getParentArray();
        if (parentArray == null || parentArray.isEmpty()) {
            return null;
        }
        Object arrayObj = getNestedValue(source, parentArray);
        if (!(arrayObj instanceof List)) {
            return null;
        }
        List<Object> sourceList = (List<Object>) arrayObj;
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (Object item : sourceList) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> itemMap = (Map<String, Object>) item;
            Map<String, Object> mappedItem = new LinkedHashMap<>();
            if (rule.getItemMapping() != null) {
                for (InboundMappingGenerator.MappingRule subRule : rule.getItemMapping()) {
                    Object resolved = resolveValue(itemMap, subRule);
                    setNestedValue(mappedItem, subRule.getTarget(), resolved);
                }
            }
            resultList.add(mappedItem);
        }
        return resultList;
    }

    /** 出站发送器接口 (SPI) */
    public interface OutboundSender {
        boolean supports(String channel);
        String send(Map<String, Object> requestEntity);
    }
}
