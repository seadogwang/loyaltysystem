package com.loyalty.platform.campaign.execution.node;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 节点处理器接口 — 所有 Campaign 节点类型的执行契约。
 */
public interface NodeHandler {

    /** 获取支持的节点类型 */
    String getNodeType();

    /** 校验配置（Schema 校验后的额外业务校验） */
    default void validateConfig(JsonNode config) throws NodeConfigValidationException {
        // 默认无额外校验
    }

    /** 执行核心逻辑 */
    NodeExecutionResult execute(NodeExecutionContext context) throws NodeExecutionException;
}
