package com.loyalty.platform.campaign.ai.skill;

import com.loyalty.platform.domain.entity.campaign.ExternalSignal;

import java.util.List;

/**
 * 外部技能接口 — 所有 AI 感知技能实现此接口。
 */
public interface ExternalSkill {

    String getSkillName();

    List<String> getCompetitorUrls();

    List<String> getKeywords();

    List<ExternalSignal> execute(SkillExecutionContext context);
}
