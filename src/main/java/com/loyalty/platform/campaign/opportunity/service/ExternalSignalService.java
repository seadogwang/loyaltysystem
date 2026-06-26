package com.loyalty.platform.campaign.opportunity.service;

import com.loyalty.platform.campaign.ai.skill.ExternalSkill;
import com.loyalty.platform.campaign.ai.skill.SkillExecutionContext;
import com.loyalty.platform.campaign.ai.skill.SkillRegistry;
import com.loyalty.platform.domain.entity.campaign.ExternalSignal;
import com.loyalty.platform.domain.repository.campaign.ExternalSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 外部信号管理服务 — 技能执行、信号管理、定时清理。
 */
@Service
@Transactional
public class ExternalSignalService {

    private static final Logger log = LoggerFactory.getLogger(ExternalSignalService.class);

    private final ExternalSignalRepository signalRepository;
    private final SkillRegistry skillRegistry;

    public ExternalSignalService(ExternalSignalRepository signalRepository,
                                  SkillRegistry skillRegistry) {
        this.signalRepository = signalRepository;
        this.skillRegistry = skillRegistry;
    }

    /**
     * 执行所有启用的外部技能（定时任务：每6小时）。
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void executeAllSkills() {
        log.info("Starting scheduled external skill execution");

        List<ExternalSkill> skills = skillRegistry.getAllEnabled();
        for (ExternalSkill skill : skills) {
            try {
                executeSkill(skill);
            } catch (Exception e) {
                log.error("Skill execution failed: {}", skill.getSkillName(), e);
            }
        }
    }

    /**
     * 执行单个技能。
     */
    public List<ExternalSignal> executeSkill(ExternalSkill skill) {
        log.info("Executing skill: {}", skill.getSkillName());

        SkillExecutionContext context = SkillExecutionContext.builder()
                .programCode("BRAND_A")
                .competitorUrls(skill.getCompetitorUrls())
                .keywords(skill.getKeywords())
                .build();

        List<ExternalSignal> signals = skill.execute(context);

        LocalDateTime now = LocalDateTime.now();
        for (ExternalSignal signal : signals) {
            signal.setId(UUID.randomUUID().toString());
            signal.setSourceSkill(skill.getSkillName());
            signal.setProgramCode(context.getProgramCode());
            signal.setCreatedAt(now);
            signalRepository.save(signal);
        }

        if (!signals.isEmpty()) {
            log.info("Skill {} produced {} signals", skill.getSkillName(), signals.size());
        }

        return signals;
    }

    /**
     * 手动触发指定技能执行。
     */
    public List<ExternalSignal> executeSkillByName(String skillName, SkillExecutionContext context) {
        ExternalSkill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + skillName);
        }

        List<ExternalSignal> signals = skill.execute(context);
        LocalDateTime now = LocalDateTime.now();
        for (ExternalSignal signal : signals) {
            signal.setId(UUID.randomUUID().toString());
            signal.setSourceSkill(skillName);
            signal.setProgramCode(context.getProgramCode());
            signal.setCreatedAt(now);
            signalRepository.save(signal);
        }
        return signals;
    }

    /**
     * 获取当前有效的信号（按 Program 过滤）。
     */
    @Transactional(readOnly = true)
    public List<ExternalSignal> getActiveSignals(String programCode) {
        return signalRepository.findActiveByProgram(programCode, LocalDateTime.now());
    }

    /**
     * 按类型和严重程度查询（按 Program 过滤）。
     */
    @Transactional(readOnly = true)
    public List<ExternalSignal> getSignalsBySeverity(String programCode, String severity) {
        return signalRepository.findByProgramAndSeverity(programCode, severity, LocalDateTime.now());
    }

    /**
     * 创建信号（Webhook 入口）。
     */
    public ExternalSignal createSignal(ExternalSignal signal) {
        if (signal.getId() == null) {
            signal.setId(UUID.randomUUID().toString());
        }
        if (signal.getCreatedAt() == null) {
            signal.setCreatedAt(LocalDateTime.now());
        }
        return signalRepository.save(signal);
    }

    /**
     * 清理过期信号（每天凌晨2点）。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredSignals() {
        int deleted = signalRepository.deleteExpiredSignals(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned {} expired external signals", deleted);
        }
    }

    /**
     * 计算外部影响因子。
     */
    public double calculateExternalWeight(List<ExternalSignal> signals, String segmentCode) {
        double weight = 1.0;

        for (ExternalSignal signal : signals) {
            if (signal.getImpactFactor() == null) continue;

            // 只影响匹配的分群
            if (signal.getAffectedSegments() != null
                    && !signal.getAffectedSegments().contains(segmentCode)) {
                continue;
            }

            String signalType = signal.getSignalType();
            double impact = signal.getImpactFactor().doubleValue();

            switch (signalType != null ? signalType : "") {
                case "PRICE_CHANGE" -> weight += impact * 0.5;
                case "VIRAL_EVENT" -> weight += impact * 0.3;
                case "SENTIMENT_SHIFT" -> weight += impact * 0.15;
                case "POLICY_CHANGE" -> weight += impact * 0.25;
                case "NEW_LAUNCH" -> weight += impact * 0.4;
                default -> weight += impact * 0.1;
            }
        }

        return Math.min(weight, 2.0);
    }

    /**
     * 获取影响指定分群的外部信号 ID 列表。
     */
    public List<String> getAffectingSignalIds(List<ExternalSignal> signals, String segmentCode) {
        List<String> ids = new ArrayList<>();
        for (ExternalSignal s : signals) {
            if (s.getAffectedSegments() != null
                    && s.getAffectedSegments().contains(segmentCode)) {
                ids.add(s.getId());
            }
        }
        return ids;
    }
}
