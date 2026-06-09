package com.loyalty.platform.flow.components;

import com.loyalty.platform.common.event.EventBridge;
import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 完成处理组件 — 发布事件到 EventBridge，清理链路状态。
 */
@LiteflowComponent("completeCmp")
public class CompleteComponent extends BaseLiteflowComponent {

    @Autowired(required = false)
    private EventBridge eventBridge;

    @Override
    protected void doProcess(EventContext ctx) throws Exception {
        // 发布事件
        if (eventBridge != null && ctx.getEventFact() != null) {
            String key = ctx.getIdempotencyKey() != null
                    ? ctx.getIdempotencyKey()
                    : "evt-" + System.currentTimeMillis();
            eventBridge.publish("loyalty-events", key, ctx.getEventFact());
            log.debug("[Complete] 事件已发布: key={}", key);
        }

        log.debug("[Complete] 流程完成: program={}, actions={}",
                ctx.getProgramCode(), ctx.getActions() != null ? ctx.getActions().size() : 0);
    }
}