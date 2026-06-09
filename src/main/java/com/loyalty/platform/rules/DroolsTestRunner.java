package com.loyalty.platform.rules;

import com.loyalty.platform.rules.action.Action;
import com.loyalty.platform.rules.action.ActionCollector;
import com.loyalty.platform.rules.action.AwardPointsAction;
import com.loyalty.platform.rules.drl.MemberFact;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Drools 规则编译与测试工具 — 验证 KieBase 编译和执行。
 *
 * <p>用于：
 * <ul>
 *   <li>CI/CD 流水线中自动验证所有 ACTIVE 规则可编译</li>
 *   <li>开发者本地快速验证单条 DRL 脚本的正确性</li>
 *   <li>AI 生成的规则自动编译校验</li>
 * </ul>
 */
@Component
public class DroolsTestRunner {

    private static final Logger log = LoggerFactory.getLogger(DroolsTestRunner.class);

    /**
     * 编译单条 DRL 脚本并返回 KieBase。
     *
     * @param drlContent DRL 规则脚本内容
     * @return 编译好的 KieBase
     * @throws KieBaseCacheManager.RuleCompileException 如果编译失败
     */
    public KieBase compileDrl(String drlContent) {
        KieHelper helper = new KieHelper();
        helper.addContent(drlContent, ResourceType.DRL);
        return helper.build();
    }

    /**
     * 用测试数据运行规则，验证输出。
     *
     * @param drlContent DRL 规则脚本
     * @param facts      测试事实（MemberFact, EventFact 等）
     * @return 规则输出的动作列表
     */
    public List<Action> testRule(String drlContent, List<Object> facts) {
        KieBase kieBase = compileDrl(drlContent);
        StatelessKieSession session = kieBase.newStatelessKieSession();

        ActionCollector collector = new ActionCollector();
        session.setGlobal("collector", collector);
        session.execute(facts);

        return collector.getActions();
    }

    /**
     * 快速示例：用会员数据测试积分发放规则。
     *
     * <pre>
     * DRL:
     * rule "TestRule"
     * when
     *   $m: MemberFact(tierCode == "GOLD")
     * then
     *   collector.awardPoints($m.getProgramCode(), String.valueOf($m.getMemberId()),
     *       "REWARD_POINTS", new java.math.BigDecimal(100), "TEST", null);
     * end
     * </pre>
     */
    public boolean verifyGoldMemberAward() {
        String drl = """
            package rules.test;
            import com.loyalty.platform.rules.drl.MemberFact;
            import com.loyalty.platform.rules.action.ActionCollector;
            import com.loyalty.platform.rules.action.AwardPointsAction;
            global ActionCollector collector;

            rule "Gold Member Bonus"
            when
                $m: MemberFact(tierCode == "GOLD")
            then
                collector.awardPoints($m.getProgramCode(), String.valueOf($m.getMemberId()),
                    "REWARD_POINTS", new java.math.BigDecimal(100), "GOLD_BONUS", null);
            end
            """;

        MemberFact member = new MemberFact("PROG001", 8821L, "GOLD", "ENROLLED", java.util.Map.of());

        List<Action> actions = testRule(drl, List.of(member));

        boolean passed = actions.size() == 1
                && actions.get(0) instanceof AwardPointsAction
                && ((AwardPointsAction) actions.get(0)).getPoints().compareTo(new BigDecimal(100)) == 0;

        log.info("[DroolsTest] Gold member award test: {}", passed ? "PASSED" : "FAILED");
        return passed;
    }
}