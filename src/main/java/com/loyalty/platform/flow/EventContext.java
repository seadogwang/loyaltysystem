package com.loyalty.platform.flow;

import com.loyalty.platform.domain.entity.EventFact;
import com.loyalty.platform.rules.action.Action;
import com.loyalty.platform.rules.drl.MemberFact;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteFlow 事件处理上下文 — 在组件链中传递的共享数据对象。
 *
 * <p>每个 SPI 请求创建一个独立的 EventContext 实例，
 * 在整个 LiteFlow 链中流转，各组件通过 getContextBean() 获取。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Data
public class EventContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户计划代码 */
    private String programCode;

    /** 原始请求体（JSON 字符串） */
    private String rawPayload;

    /** 渠道标识 */
    private String channel;

    /** 幂等键 */
    private String idempotencyKey;

    /** 标准化后的 payload */
    private Map<String, Object> standardizedPayload;

    /** 会员事实（One-ID 匹配后填充） */
    private MemberFact memberFact;

    /** 事件事实（FactBuilder 构建） */
    private EventFact eventFact;

    /** 规则引擎输出的动作列表 */
    private List<Action> actions = new ArrayList<>();

    /** 处理是否失败 */
    private boolean processingFailed;

    /** 错误信息 */
    private String errorMessage;

    /** 扩展属性 */
    private Map<String, Object> attributes = new HashMap<>();

    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) this.attributes.get(key);
    }
}