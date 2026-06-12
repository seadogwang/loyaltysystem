package com.loyalty.platform.flow.mapping;

import java.util.List;
import java.util.Map;

/**
 * 入站映射脚本生成器 — 根据映射配置生成 GraalVM/JavaScript 转换脚本。
 *
 * <p>脚本功能: 将外部 API 响应 (source) 转换为标准 TransactionEvent 格式。
 * 映射类型支持: PATH (直接映射), EXPRESSION (表达式), CONSTANT (常量)。
 *
 * @see entity_config.md 第7.2节
 */
public class InboundMappingGenerator {

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

        sb.append("    // 字段映射\n");
        for (MappingRule rule : mappings) {
            switch (rule.getType()) {
                case "PATH":
                    sb.append("    event.payload.").append(rule.getTarget())
                      .append(" = source.").append(rule.getSource()).append(";\n");
                    break;
                case "EXPRESSION":
                    sb.append("    event.payload.").append(rule.getTarget())
                      .append(" = ").append(rule.getExpression())
                      .append("(source.").append(rule.getSource()).append(");\n");
                    break;
                case "CONSTANT":
                    sb.append("    event.payload.").append(rule.getTarget())
                      .append(" = '").append(rule.getConstant()).append("';\n");
                    break;
                default:
                    break;
            }
        }

        sb.append("\n    return event;\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 映射规则 */
    public static class MappingRule {
        private String source;
        private String target;
        private String type;       // PATH, EXPRESSION, CONSTANT
        private String expression;
        private String constant;

        public MappingRule() {}

        public MappingRule(String source, String target, String type) {
            this.source = source; this.target = target; this.type = type;
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
    }
}