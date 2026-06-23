package com.loyalty.platform.flow.mapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 入站映射脚本生成器 — 根据映射配置生成 GraalVM/JavaScript 转换脚本。
 *
 * <p>脚本功能: 将外部 API 响应 (source) 转换为标准 TransactionEvent 格式。
 * 映射类型支持:
 * <ul>
 *   <li>{@code PATH} — 直接路径映射 (a.b.c 嵌套路径)
 *   <li>{@code EXPRESSION} — 函数表达式转换 (仅限白名单标识符)
 *   <li>{@code CONSTANT} — 常量映射
 *   <li>{@code SCRIPT} — 自定义 JavaScript 脚本 (信任边界: 仅管理员可配置)
 *   <li>{@code ARRAY_MAPPING} — 嵌套数组映射
 * </ul>
 *
 * <p><b>安全约束</b>: EXPRESSION 类型仅允许白名单中的 JavaScript 标识符，
 * 防止代码注入。SCRIPT 类型允许任意 JavaScript 但运行在 GraalVM 受限沙箱中，
 * 且仅管理员可配置脚本内容——属于信任边界。
 *
 * @see entity_config.md 第7.2节
 */
public class InboundMappingGenerator {

    /** EXPRESSION 类型允许的 JavaScript 标识符白名单 — 防止代码注入 */
    private static final Set<String> ALLOWED_EXPRESSIONS = Set.of(
            "Number", "parseInt", "parseFloat", "String", "Boolean",
            "encodeURIComponent", "decodeURIComponent", "Date",
            "Math.floor", "Math.ceil", "Math.round", "Math.abs",
            "toString", "toISOString", "formatDate",
            "concat", "toNumber", "default"
    );

    private InboundMappingGenerator() {}

    /**
     * 生成入站转换脚本。
     *
     * @param channel      渠道标识 (TMALL, JD, etc.)
     * @param eventType    事件类型 (ORDER, BEHAVIOR, etc.)
     * @param mappings     映射规则列表
     * @return JavaScript 函数字符串
     */
    public static String generateScript(String channel, String eventType,
                                         List<MappingRule> mappings) {
        StringBuilder sb = new StringBuilder();
        sb.append("function transform(source, context) {\n");
        sb.append("    var event = {\n");
        sb.append("        eventType: '").append(eventType).append("',\n");
        sb.append("        channel: '").append(channel).append("',\n");
        sb.append("        timestamp: new Date().toISOString(),\n");
        sb.append("        payload: {}\n");
        sb.append("    };\n\n");

        // 幂等键提取
        sb.append("    // 幂等键\n");
        sb.append("    var idempotentKey = source.tid || source.orderId || source.id || '';\n");
        sb.append("    if (idempotentKey) {\n");
        sb.append("        context.setIdentity(idempotentKey);\n");
        sb.append("        event.idempotentKey = idempotentKey;\n");
        sb.append("    }\n\n");

        // 辅助函数: 安全访问嵌套路径
        sb.append("    // 辅助: 安全嵌套取值\n");
        sb.append("    function safeGet(obj, path) {\n");
        sb.append("        var parts = path.split('.');\n");
        sb.append("        var cur = obj;\n");
        sb.append("        for (var i = 0; i < parts.length; i++) {\n");
        sb.append("            if (cur === null || cur === undefined || typeof cur !== 'object') return undefined;\n");
        sb.append("            cur = cur[parts[i]];\n");
        sb.append("        }\n");
        sb.append("        return cur;\n");
        sb.append("    }\n\n");

        // 辅助: 设置嵌套值
        sb.append("    function safeSet(obj, path, value) {\n");
        sb.append("        var parts = path.split('.');\n");
        sb.append("        var cur = obj;\n");
        sb.append("        for (var i = 0; i < parts.length - 1; i++) {\n");
        sb.append("            if (cur[parts[i]] === undefined || cur[parts[i]] === null) {\n");
        sb.append("                cur[parts[i]] = {};\n");
        sb.append("            }\n");
        sb.append("            cur = cur[parts[i]];\n");
        sb.append("        }\n");
        sb.append("        cur[parts[parts.length - 1]] = value;\n");
        sb.append("    }\n\n");

        sb.append("    // 字段映射\n");
        for (MappingRule rule : mappings) {
            appendMappingRule(sb, rule, "event.payload", "source", 1);
        }

        sb.append("\n    return event;\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 递归追加映射规则到脚本 */
    private static void appendMappingRule(StringBuilder sb, MappingRule rule,
                                          String targetBase, String sourceBase, int depth) {
        String indent = "    ".repeat(Math.max(1, depth));

        switch (rule.getType()) {
            case "PATH":
                // 使用 safeGet 安全取值
                sb.append(indent).append("safeSet(").append(targetBase)
                  .append(", '").append(rule.getTarget()).append("', ")
                  .append("safeGet(").append(sourceBase).append(", '").append(rule.getSource()).append("'));\n");
                break;

            case "EXPRESSION":
                // 安全验证: 仅允许白名单中的表达式标识符，防止代码注入
                validateExpression(rule.getExpression());
                // 解析嵌套路径取值 + 表达式调用，加入空值保护
                sb.append(indent).append("{\n");
                sb.append(indent).append("    var __val = safeGet(").append(sourceBase)
                  .append(", '").append(rule.getSource()).append("');\n");
                sb.append(indent).append("    if (__val !== undefined && __val !== null) {\n");
                sb.append(indent).append("        safeSet(").append(targetBase)
                  .append(", '").append(rule.getTarget()).append("', ")
                  .append(rule.getExpression()).append("(__val));\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
                break;

            case "CONSTANT":
                sb.append(indent).append("safeSet(").append(targetBase)
                  .append(", '").append(rule.getTarget())
                  .append("', '").append(escapeJsString(rule.getConstant())).append("');\n");
                break;

            case "SCRIPT":
                // 信任边界: SCRIPT 内容由管理员配置，运行在 GraalVM 受限沙箱中
                // 直接内联脚本，source 作为上下文变量
                sb.append(indent).append("{\n");
                sb.append(indent).append("    var __scriptSource = ").append(sourceBase).append(";\n");
                sb.append(indent).append("    var __scriptResult = (function() {\n");
                // 将脚本内容缩进后内联
                if (rule.getScript() != null && !rule.getScript().isBlank()) {
                    String[] scriptLines = rule.getScript().split("\n");
                    for (String line : scriptLines) {
                        sb.append(indent).append("        ").append(line).append("\n");
                    }
                }
                sb.append(indent).append("    })();\n");
                sb.append(indent).append("    if (__scriptResult !== undefined && __scriptResult !== null) {\n");
                sb.append(indent).append("        safeSet(").append(targetBase)
                  .append(", '").append(rule.getTarget()).append("', __scriptResult);\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
                break;

            case "ARRAY_MAPPING":
                // 遍历父数组
                String parentArray = rule.getParentArray();
                String arrayVar = "__arr_" + rule.getTarget().replace(".", "_");
                sb.append(indent).append("{\n");
                sb.append(indent).append("    var ").append(arrayVar)
                  .append(" = safeGet(").append(sourceBase)
                  .append(", '").append(parentArray).append("');\n");
                sb.append(indent).append("    if (Array.isArray(").append(arrayVar).append(")) {\n");
                sb.append(indent).append("        var __mapped = ").append(arrayVar).append(".map(function(__item) {\n");
                sb.append(indent).append("            var __result = {};\n");
                // 子映射
                if (rule.getItemMapping() != null && !rule.getItemMapping().isEmpty()) {
                    for (MappingRule subRule : rule.getItemMapping()) {
                        appendMappingRule(sb, subRule, "__result", "__item", depth + 2);
                    }
                }
                sb.append(indent).append("            return __result;\n");
                sb.append(indent).append("        });\n");
                sb.append(indent).append("        safeSet(").append(targetBase)
                  .append(", '").append(rule.getTarget()).append("', __mapped);\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
                break;

            default:
                break;
        }
    }

    /** 验证表达式标识符是否在白名单中 — 防止代码注入 */
    private static void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("映射表达式不能为空");
        }
        if (!ALLOWED_EXPRESSIONS.contains(expression.trim())) {
            throw new IllegalArgumentException(
                    "不安全的映射表达式: '" + expression + "' — 仅允许白名单标识符: " + ALLOWED_EXPRESSIONS);
        }
    }

    /** 转义 JS 字符串中的特殊字符 */
    private static String escapeJsString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("'", "\\'")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    /** 映射规则 */
    public static class MappingRule {
        private String source;
        private String target;
        private String type;       // PATH, EXPRESSION, CONSTANT, SCRIPT, ARRAY_MAPPING
        private String expression;
        private String constant;
        private String script;
        private String parentArray;
        private List<MappingRule> itemMapping;

        public MappingRule() {}

        public MappingRule(String source, String target, String type) {
            this.source = source;
            this.target = target;
            this.type = type;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
        public String getConstant() { return constant; }
        public void setConstant(String constant) { this.constant = constant; }
        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }
        public String getParentArray() { return parentArray; }
        public void setParentArray(String parentArray) { this.parentArray = parentArray; }
        public List<MappingRule> getItemMapping() { return itemMapping; }
        public void setItemMapping(List<MappingRule> itemMapping) { this.itemMapping = itemMapping; }
    }
}
