package com.loyalty.platform.flow.components;

import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.loyalty.platform.rules.RewardExecutor;
import com.loyalty.platform.rules.action.Action;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 动作执行组件 — 在单事务中执行规则引擎输出的所有 Action。
 *
 * <p>调用 RewardExecutor.executeActions() 执行积分发放、等级变更等动作。
 * 所有动作在同一事务中执行，任何一步失败则全部回滚。
 */
@LiteflowComponent("actionExecuteCmp")
public class ActionExecuteComponent extends BaseLiteflowComponent {

    @Autowired
    private RewardExecutor rewardExecutor;

    @Override
    protected void doProcess(EventContext ctx) throws Exception {
        List<Action> actions = ctx.getActions();
        if (actions == null || actions.isEmpty()) {
            log.debug("[ActionExecute] 无动作需要执行");
            return;
        }

        Long memberId = ctx.getMemberFact() != null ? ctx.getMemberFact().getMemberId() : 0L;
        rewardExecutor.executeActions(memberId, actions);

        log.debug("[ActionExecute] 执行完成: {} actions", actions.size());
    }
}