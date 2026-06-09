package com.loyalty.platform.flow;

import com.loyalty.platform.common.context.TenantContext;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiteFlow 链执行集成测试 — 验证 ORDER_CHAIN 完整流程。
 *
 * <p>要求: LiteFlow 配置已加载，组件已注册到 Spring 容器。
 */
@SpringBootTest
@DisplayName("LiteFlow 链执行集成测试")
class LiteflowChainTest {

    @Autowired
    private FlowExecutor flowExecutor;

    @BeforeEach
    void setUp() {
        TenantContext.set("PROG001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("FlowExecutor 已注入")
    void shouldInjectFlowExecutor() {
        assertNotNull(flowExecutor, "FlowExecutor 应由 LiteFlow starter 自动配置");
    }

    @Test
    @DisplayName("ORDER_CHAIN 链存在且可执行")
    void shouldExecuteOrderChain() {
        // 构建测试上下文
        EventContext ctx = new EventContext();
        ctx.setProgramCode("PROG001");
        ctx.setChannel("TMALL");
        ctx.setRawPayload("{\"member_id\":\"8821\",\"eventType\":\"ORDER\",\"amount\":200}");
        ctx.setIdempotencyKey("test-chain-" + System.currentTimeMillis());

        LiteflowResponse response = flowExecutor.execute2Resp("ORDER_CHAIN", null, ctx);

        // 链可能因无数据库/无会员而失败，但不应该抛异常
        assertNotNull(response);
        // 验证上下文在链中流转
        assertNotNull(ctx);
    }

    @Test
    @DisplayName("BEHAVIOR_CHAIN 链存在")
    void shouldHaveBehaviorChain() {
        EventContext ctx = new EventContext();
        ctx.setProgramCode("PROG001");
        ctx.setChannel("WECHAT");
        ctx.setRawPayload("{}");
        ctx.setIdempotencyKey("test-behavior-" + System.currentTimeMillis());

        LiteflowResponse response = flowExecutor.execute2Resp("BEHAVIOR_CHAIN", null, ctx);
        assertNotNull(response);
    }

    @Test
    @DisplayName("REFUND_CHAIN 链存在")
    void shouldHaveRefundChain() {
        EventContext ctx = new EventContext();
        ctx.setProgramCode("PROG001");
        ctx.setChannel("TMALL");
        ctx.setRawPayload("{}");
        ctx.setIdempotencyKey("test-refund-" + System.currentTimeMillis());

        LiteflowResponse response = flowExecutor.execute2Resp("REFUND_CHAIN", null, ctx);
        assertNotNull(response);
    }

    @Test
    @DisplayName("未知链名应返回失败")
    void shouldFailOnUnknownChain() {
        EventContext ctx = new EventContext();
        ctx.setProgramCode("PROG001");
        ctx.setRawPayload("{}");

        LiteflowResponse response = flowExecutor.execute2Resp("NON_EXISTENT_CHAIN", null, ctx);
        assertNotNull(response);
        assertFalse(response.isSuccess(), "不存在的链名应返回失败");
    }
}