package com.loyalty.platform.api.dto;

import java.util.List;

/**
 * 映射规则 DTO — 用于前端可视化配置 API 与 Business Entity 之间的字段映射。
 *
 * <p>映射类型:
 * <ul>
 *   <li>{@code PATH} — 直接路径映射 (a.b.c)</li>
 *   <li>{@code EXPRESSION} — 函数表达式转换 (parseFloat, toISOString 等)</li>
 *   <li>{@code CONSTANT} — 常量映射</li>
 *   <li>{@code SCRIPT} — 自定义 JavaScript 脚本</li>
 *   <li>{@code ARRAY_MAPPING} — 嵌套数组映射</li>
 * </ul>
 */
public class MappingRuleDto {

    /** 源字段名（支持 a.b.c 嵌套路径） */
    private String source;

    /** 目标字段名 */
    private String target;

    /** 映射类型: PATH, EXPRESSION, CONSTANT, SCRIPT, ARRAY_MAPPING */
    private String type;

    /** 表达式名称（仅 EXPRESSION 类型使用） */
    private String expression;

    /** 自定义脚本（仅 SCRIPT 类型使用） */
    private String script;

    /** 常量值（仅 CONSTANT 类型使用） */
    private String constant;

    /** 父数组字段名（仅 ARRAY_MAPPING 类型下的子元素使用） */
    private String parentArray;

    /** 子映射规则（仅 ARRAY_MAPPING 类型使用） */
    private List<MappingRuleDto> itemMapping;

    public MappingRuleDto() {
    }

    public MappingRuleDto(String source, String target, String type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    // ---- getters / setters ----

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getConstant() {
        return constant;
    }

    public void setConstant(String constant) {
        this.constant = constant;
    }

    public String getParentArray() {
        return parentArray;
    }

    public void setParentArray(String parentArray) {
        this.parentArray = parentArray;
    }

    public List<MappingRuleDto> getItemMapping() {
        return itemMapping;
    }

    public void setItemMapping(List<MappingRuleDto> itemMapping) {
        this.itemMapping = itemMapping;
    }
}
