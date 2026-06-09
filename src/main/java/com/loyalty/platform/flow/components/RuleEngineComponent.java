package com.loyalty.platform.flow.components;

import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.loyalty.platform.rules.RuleEngineService;
import com.loyalty.platform.rules.action.Action;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 规则引擎组件 — 调用 Drools StatelessKieSession 推理。
 *
 * <p>将 EventContext 中的 EventFact + MemberFact 传入 RuleEngineService，
 * 获取规则匹配后的 Action 列表，设置到 ctx.actions。
 */
@LiteflowComponent("ruleEngineCmp")
public class RuleEngineComponent extends BaseLiteflowComponent {

    @Autowired
    private RuleEngineService ruleEngine;

    @Override
    protected void doProcess(EventContext ctx) throws Exception {
        if (ctx.getEventFact() == null) {
            log.warn("[RuleEngine] eventFact 为空，跳过规则推理");
            return;
        }

        List<Object> facts = new java.util.ArrayList<>();
        facts.add(ctx.getEventFact());
        if (ctx.getMemberFact() != null) {
            facts.add(ctx.getMemberFact());
        }

        List<Action> actions = ruleEngine.evaluate(ctx.getProgramCode(), facts);
        ctx.setActions(actions);

        log.debug("[RuleEngine] 推理完成: {} facts → {} actions", facts.size(), actions.size());
    }
}