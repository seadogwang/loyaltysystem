package com.loyalty.platform.flow;

import com.yomahub.liteflow.core.NodeComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LiteFlow 组件抽象基类 — 统一计时和异常处理。
 *
 * <p>所有业务组件继承此类，实现 {@link #doProcess} 方法。
 * 基类自动处理：
 * <ul>
 *   <li>执行耗时统计</li>
 *   <li>异常标记（设置 processingFailed + errorMessage）</li>
 *   <li>异常传播（抛出原始异常让 LiteFlow 处理）</li>
 * </ul>
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
public abstract class BaseLiteflowComponent extends NodeComponent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 获取事件上下文。
     */
    protected EventContext getEventContext() {
        return this.getContextBean(EventContext.class);
    }

    /**
     * 子类实现具体的业务逻辑。
     *
     * @param ctx 事件上下文
     * @throws Exception 业务异常
     */
    protected abstract void doProcess(EventContext ctx) throws Exception;

    @Override
    public void process() throws Exception {
        EventContext ctx = getEventContext();
        long start = System.currentTimeMillis();
        try {
            doProcess(ctx);
        } catch (Exception e) {
            ctx.setProcessingFailed(true);
            ctx.setErrorMessage(e.getMessage());
            log.error("[{}] 处理失败: {}", getClass().getSimpleName(), e.getMessage());
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (log.isDebugEnabled()) {
                log.debug("[{}] 耗时 {}ms", getClass().getSimpleName(), duration);
            }
        }
    }

    @Override
    public boolean isAccess() {
        return true;
    }
}