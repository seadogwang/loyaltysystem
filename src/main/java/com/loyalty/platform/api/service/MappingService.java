package com.loyalty.platform.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.api.dto.MappingRuleDto;
import com.loyalty.platform.domain.entity.ChannelAdapterConfig;
import com.loyalty.platform.domain.repository.ChannelAdapterConfigRepository;
import jakarta.annotation.PostConstruct;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 映射配置管理服务 — 管理渠道适配器的入站/出站字段映射规则。
 *
 * <p>存储策略：映射规则持久化到 {@code channel_adapter_config} 表的
 * {@code inbound_mappings} / {@code outbound_mappings} JSONB 列。
 *
 * <p>映射规则结构：{@code Map<operationCode, List<MappingRuleDto>>}
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Service
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);

    private final ChannelAdapterConfigRepository configRepo;
    private final ObjectMapper objectMapper;

    public MappingService(ChannelAdapterConfigRepository configRepo, ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.objectMapper = objectMapper;
    }

    // ======================== 默认种子数据 ========================

    @PostConstruct
    public void initDefaultData() {
        // 检查是否已有默认渠道配置
        Optional<ChannelAdapterConfig> existing = configRepo.findByProgramCodeAndChannel("DEFAULT", "tmall");
        if (existing.isPresent() && hasMappings(existing.get())) {
            log.info("[MappingService] 数据库已有映射数据，跳过种子数据初始化");
            return;
        }

        log.info("[MappingService] 初始化默认映射种子数据...");

        // 天猫 (tmall) channel — orderCreate 入站映射
        List<MappingRuleDto> inboundRules = List.of(
                rule("tid", "orderId", "PATH"),
                rule("payment", "totalAmount", "EXPRESSION", "parseFloat"),
                rule("pay_time", "paidAt", "EXPRESSION", "toISOString")
        );

        // 天猫 (tmall) channel — orderCreate 出站映射
        List<MappingRuleDto> outboundRules = List.of(
                rule("orderId", "out_trade_no", "PATH"),
                rule("totalAmount", "total_amount", "EXPRESSION", "toString")
        );

        ChannelAdapterConfig config = existing.orElseGet(() ->
                ChannelAdapterConfig.builder()
                        .programCode("DEFAULT")
                        .channel("tmall")
                        .authConfig(Map.of())
                        .requestMapping(Map.of())
                        .responseMapping(Map.of())
                        .rateLimitConfig(Map.of())
                        .status("ACTIVE")
                        .build()
        );

        Map<String, Object> inboundJson = Map.of("orderCreate", toRawList(inboundRules));
        Map<String, Object> outboundJson = Map.of("orderCreate", toRawList(outboundRules));
        config.setInboundMappings(inboundJson);
        config.setOutboundMappings(outboundJson);
        configRepo.save(config);

        log.info("[MappingService] 默认映射种子数据已保存: channel=tmall, inbound={}, outbound={}",
                inboundRules.size(), outboundRules.size());
    }

    private boolean hasMappings(ChannelAdapterConfig config) {
        return (config.getInboundMappings() != null && !config.getInboundMappings().isEmpty())
                || (config.getOutboundMappings() != null && !config.getOutboundMappings().isEmpty());
    }

    /** 构造简单规则 (PATH) */
    private static MappingRuleDto rule(String source, String target, String type) {
        return new MappingRuleDto(source, target, type);
    }

    /** 构造表达式规则 */
    private static MappingRuleDto rule(String source, String target, String type, String expression) {
        MappingRuleDto r = new MappingRuleDto(source, target, type);
        r.setExpression(expression);
        return r;
    }

    // ======================== 渠道配置获取/创建 ========================

    /**
     * 获取或创建渠道适配器配置。
     */
    private ChannelAdapterConfig getOrCreateConfig(String programCode, String channel) {
        return configRepo.findByProgramCodeAndChannel(programCode, channel)
                .orElseGet(() -> {
                    ChannelAdapterConfig config = ChannelAdapterConfig.builder()
                            .programCode(programCode)
                            .channel(channel)
                            .authConfig(Map.of())
                            .requestMapping(Map.of())
                            .responseMapping(Map.of())
                            .rateLimitConfig(Map.of())
                            .status("ACTIVE")
                            .build();
                    return configRepo.save(config);
                });
    }

    // ======================== 入站映射 ========================

    /**
     * 获取入站映射规则列表。
     */
    @SuppressWarnings("unchecked")
    public List<MappingRuleDto> getInboundMappings(String programCode, String channel, String operationCode) {
        Optional<ChannelAdapterConfig> opt = configRepo.findByProgramCodeAndChannel(programCode, channel);
        if (opt.isEmpty()) return Collections.emptyList();

        Map<String, Object> inbound = opt.get().getInboundMappings();
        if (inbound == null) return Collections.emptyList();

        Object raw = inbound.get(operationCode);
        if (raw == null) return Collections.emptyList();

        return fromRawList(raw);
    }

    /**
     * 保存入站映射规则列表。
     */
    public void saveInboundMappings(String programCode, String channel, String operationCode,
                                    List<MappingRuleDto> rules) {
        ChannelAdapterConfig config = getOrCreateConfig(programCode, channel);
        Map<String, Object> inbound = new LinkedHashMap<>(config.getInboundMappings() != null
                ? config.getInboundMappings() : Map.of());
        inbound.put(operationCode, toRawList(rules));
        config.setInboundMappings(inbound);
        configRepo.save(config);
        log.info("[MappingService] 入站映射已保存: channel={}, operationCode={}, rules={}",
                channel, operationCode, rules.size());
    }

    // ======================== 出站映射 ========================

    /**
     * 获取出站映射规则列表。
     */
    @SuppressWarnings("unchecked")
    public List<MappingRuleDto> getOutboundMappings(String programCode, String channel, String operationCode) {
        Optional<ChannelAdapterConfig> opt = configRepo.findByProgramCodeAndChannel(programCode, channel);
        if (opt.isEmpty()) return Collections.emptyList();

        Map<String, Object> outbound = opt.get().getOutboundMappings();
        if (outbound == null) return Collections.emptyList();

        Object raw = outbound.get(operationCode);
        if (raw == null) return Collections.emptyList();

        return fromRawList(raw);
    }

    /**
     * 保存出站映射规则列表。
     */
    public void saveOutboundMappings(String programCode, String channel, String operationCode,
                                     List<MappingRuleDto> rules) {
        ChannelAdapterConfig config = getOrCreateConfig(programCode, channel);
        Map<String, Object> outbound = new LinkedHashMap<>(config.getOutboundMappings() != null
                ? config.getOutboundMappings() : Map.of());
        outbound.put(operationCode, toRawList(rules));
        config.setOutboundMappings(outbound);
        configRepo.save(config);
        log.info("[MappingService] 出站映射已保存: channel={}, operationCode={}, rules={}",
                channel, operationCode, rules.size());
    }

    // ======================== 测试映射 ========================

    /**
     * 测试执行映射规则 — 使用 GraalVM JavaScript 引擎执行 SCRIPT 类型规则。
     *
     * @param sourceJson 源 JSON 字符串
     * @param rules      映射规则列表
     * @return 映射结果 (Map 形式)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> testMapping(String sourceJson, List<MappingRuleDto> rules) {
        // 1. 解析 sourceJson 为 Map
        Map<String, Object> sourceMap;
        try {
            sourceMap = objectMapper.readValue(sourceJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("无法解析 sourceJson: " + e.getMessage(), e);
        }

        // 2. 执行映射
        Map<String, Object> result = new LinkedHashMap<>();
        for (MappingRuleDto rule : rules) {
            Object resolved = resolveValue(sourceMap, rule);
            setNestedValue(result, rule.getTarget(), resolved);
        }
        return result;
    }

    /** 根据映射规则从源数据中解析值 */
    @SuppressWarnings("unchecked")
    private Object resolveValue(Map<String, Object> source, MappingRuleDto rule) {
        return switch (rule.getType()) {
            case "PATH" -> getNestedValue(source, rule.getSource());
            case "EXPRESSION" -> {
                Object raw = getNestedValue(source, rule.getSource());
                yield applyExpression(raw, rule.getExpression());
            }
            case "CONSTANT" -> rule.getConstant();
            case "SCRIPT" -> executeScript(source, rule.getScript());
            case "ARRAY_MAPPING" -> resolveArrayMapping(source, rule);
            default -> null;
        };
    }

    /** 嵌套路径取值: "a.b.c" -> source["a"]["b"]["c"] */
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

    /** 执行表达式转换 */
    private Object applyExpression(Object value, String expression) {
        if (expression == null || value == null) return value;
        return switch (expression) {
            case "toString" -> String.valueOf(value);
            case "toISOString" -> {
                if (value instanceof Date) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    yield sdf.format((Date) value);
                }
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
            case "formatDate" -> String.valueOf(value);
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

    /** 执行自定义 SCRIPT 脚本 (GraalVM JavaScript) */
    @SuppressWarnings("unchecked")
    private Object executeScript(Map<String, Object> source, String script) {
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
            log.warn("[MappingService] SCRIPT 执行失败: {}", e.getMessage());
            return null;
        }
    }

    /** 处理 ARRAY_MAPPING 类型 — 遍历父数组并对每个元素应用子映射 */
    @SuppressWarnings("unchecked")
    private Object resolveArrayMapping(Map<String, Object> source, MappingRuleDto rule) {
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
                for (MappingRuleDto subRule : rule.getItemMapping()) {
                    Object resolved = resolveValue(itemMap, subRule);
                    setNestedValue(mappedItem, subRule.getTarget(), resolved);
                }
            }
            resultList.add(mappedItem);
        }
        return resultList;
    }

    /** 设置嵌套值到 Map (a.b.c -> map["a"]["b"]["c"] = value) */
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

    // ======================== JSON 序列化辅助 ========================

    /**
     * 将 MappingRuleDto 列表转为通用 List&lt;Map&gt;（存入 JSONB）。
     */
    private List<Map<String, Object>> toRawList(List<MappingRuleDto> rules) {
        return rules.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", r.getSource());
            m.put("target", r.getTarget());
            m.put("type", r.getType());
            if (r.getExpression() != null) m.put("expression", r.getExpression());
            if (r.getScript() != null) m.put("script", r.getScript());
            if (r.getConstant() != null) m.put("constant", r.getConstant());
            if (r.getParentArray() != null) m.put("parentArray", r.getParentArray());
            if (r.getItemMapping() != null && !r.getItemMapping().isEmpty()) {
                m.put("itemMapping", toRawList(r.getItemMapping()));
            }
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 从通用 List&lt;Map&gt; 还原为 MappingRuleDto 列表（从 JSONB 读取）。
     */
    @SuppressWarnings("unchecked")
    private List<MappingRuleDto> fromRawList(Object raw) {
        if (!(raw instanceof List)) return Collections.emptyList();
        List<Object> rawList = (List<Object>) raw;
        return rawList.stream().map(item -> {
            if (!(item instanceof Map)) return null;
            Map<String, Object> m = (Map<String, Object>) item;
            MappingRuleDto dto = new MappingRuleDto(
                    (String) m.get("source"),
                    (String) m.get("target"),
                    (String) m.get("type")
            );
            if (m.get("expression") != null) dto.setExpression((String) m.get("expression"));
            if (m.get("script") != null) dto.setScript((String) m.get("script"));
            if (m.get("constant") != null) dto.setConstant((String) m.get("constant"));
            if (m.get("parentArray") != null) dto.setParentArray((String) m.get("parentArray"));
            if (m.get("itemMapping") != null) {
                dto.setItemMapping(fromRawList(m.get("itemMapping")));
            }
            return dto;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
