package com.loyalty.platform.api.dto;

import java.util.List;

/**
 * 测试映射请求 DTO。
 */
public class TestMappingRequestDto {

    /** 源 JSON 字符串 */
    private String sourceJson;

    /** 映射规则列表 */
    private List<MappingRuleDto> mappings;

    public TestMappingRequestDto() {
    }

    public String getSourceJson() {
        return sourceJson;
    }

    public void setSourceJson(String sourceJson) {
        this.sourceJson = sourceJson;
    }

    public List<MappingRuleDto> getMappings() {
        return mappings;
    }

    public void setMappings(List<MappingRuleDto> mappings) {
        this.mappings = mappings;
    }
}
