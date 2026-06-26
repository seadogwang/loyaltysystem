package com.loyalty.platform.campaign.ai.skill;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 技能执行上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillExecutionContext {
    private String programCode;
    private List<String> competitorUrls;
    private List<String> keywords;
    private Map<String, Object> extraParams;
}
