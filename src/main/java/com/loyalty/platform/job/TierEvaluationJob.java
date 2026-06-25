package com.loyalty.platform.job;

import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.domain.entity.TierDefinition;
import com.loyalty.platform.domain.repository.MemberRepository;
import com.loyalty.platform.domain.repository.TierDefinitionRepository;
import com.loyalty.platform.rules.TierEvaluationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会员等级保降级定时任务 — 每天凌晨执行。
 *
 * <p>扫描所有 ENROLLED 状态的会员，调用 {@link TierEvaluationService}
 * 执行保级/降级评估。等级阈值从 {@code tier_definition} 表动态读取。
 *
 * <p><b>租户隔离</b>：继承 {@link TenantAwareJob}，通过 {@code forEachTenant}
 * 在每个租户的处理前后严格 {@code set()/clear()} TenantContext。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.8.0
 */
@Component
public class TierEvaluationJob extends TenantAwareJob {

    /** cron: 每天凌晨 3:00 执行 */
    private static final String CRON = "0 0 3 * * ?";

    private final TierEvaluationService tierEvalService;
    private final TierDefinitionRepository tierDefRepo;
    private final MemberRepository memberRepo;

    @PersistenceContext
    private EntityManager em;

    public TierEvaluationJob(TierEvaluationService tierEvalService,
                             TierDefinitionRepository tierDefRepo,
                             MemberRepository memberRepo) {
        this.tierEvalService = tierEvalService;
        this.tierDefRepo = tierDefRepo;
        this.memberRepo = memberRepo;
    }

    @Override
    protected String getJobName() { return "TierEvaluationJob"; }

    /**
     * 每日凌晨触发等级保降级评估。
     */
    @Scheduled(cron = CRON)
    public void execute() {
        if (log.isDebugEnabled()) {
            log.debug("[TierEvaluationJob] 触发定时任务");
        }
        forEachTenant(this::evaluateTenant);
    }

    /**
     * 对单个租户执行保级/降级评估。
     *
     * <p>流程：
     * <ol>
     *   <li>查询租户下所有已入会的会员</li>
     *   <li>跳过 BASE 等级会员（无需保级）</li>
     *   <li>调用 {@link TierEvaluationService#evaluateMemberTier} 评估保级/降级</li>
     * </ol>
     */
    @Transactional
    void evaluateTenant(String programCode) {
        // 查询该租户下所有已入会且非 BASE 等级的会员
        List<Member> members = memberRepo.findByProgramCodeAndStatus(programCode, "ENROLLED");

        // 获取等级定义（用于判断哪些等级需要保级评估）
        List<TierDefinition> tierDefs = tierDefRepo.findByProgramCodeOrderBySequenceAsc(programCode);

        int evaluatedCount = 0;
        int changedCount = 0;

        for (Member member : members) {
            String currentTier = member.getTierCode();

            // BASE 等级无需保级评估
            if (currentTier == null || "BASE".equals(currentTier)) {
                continue;
            }

            // 检查该等级是否配置了保级规则
            TierDefinition tierDef = tierDefs.stream()
                    .filter(t -> t.getTierCode().equals(currentTier))
                    .findFirst().orElse(null);

            if (tierDef == null || tierDef.getDowngradeCriteria() == null
                    || tierDef.getDowngradeCriteria().isEmpty()) {
                // 未配置保级规则，跳过
                continue;
            }

            try {
                String oldTier = member.getTierCode();
                tierEvalService.evaluateMemberTier(programCode, member.getMemberId(), "RETENTION", null);
                evaluatedCount++;

                // 刷新 member 以获取最新等级
                em.flush();
                em.refresh(member);

                if (!oldTier.equals(member.getTierCode())) {
                    changedCount++;
                }
            } catch (Exception e) {
                log.warn("[TierEvaluationJob] 会员 {} 评估失败: {}", member.getMemberId(), e.getMessage());
            }

            // 限制单次处理数量，防止 OOM
            if (evaluatedCount >= 500) {
                log.info("[TierEvaluationJob] 达到单次处理上限 500，剩余会员将在下次任务处理");
                break;
            }
        }

        log.info("[TierEvaluationJob] 租户 [{}] 评估完成: 评估 {} 个, 变更 {} 个",
                programCode, evaluatedCount, changedCount);
    }
}