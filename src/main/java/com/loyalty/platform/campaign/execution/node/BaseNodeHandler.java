package com.loyalty.platform.campaign.execution.node;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * 节点处理器基类 — 模板方法：校验 → 执行 → 构建结果。
 */
public abstract class BaseNodeHandler implements NodeHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        String nodeId = context.getNodeId();
        long startTime = System.currentTimeMillis();
        log.info("Node execution started: nodeId={}, type={}", nodeId, getNodeType());

        try {
            validateConfig(context.getConfig());
            Map<String, Object> outputs = doExecute(context);
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Node execution completed: nodeId={}, duration={}ms", nodeId, durationMs);
            return NodeExecutionResult.builder().nodeId(nodeId).status("SUCCESS")
                    .outputs(outputs).durationMs(durationMs).build();
        } catch (NodeConfigValidationException e) {
            log.warn("Node config validation failed: nodeId={}, error={}", nodeId, e.getMessage());
            return NodeExecutionResult.builder().nodeId(nodeId).status("FAILED")
                    .errorMessage("Config: " + e.getMessage()).durationMs(System.currentTimeMillis() - startTime).build();
        } catch (NodeExecutionException e) {
            log.error("Node execution failed: nodeId={}, error={}", nodeId, e.getMessage());
            return NodeExecutionResult.builder().nodeId(nodeId).status("FAILED")
                    .errorMessage(e.getMessage()).durationMs(System.currentTimeMillis() - startTime).build();
        } catch (Exception e) {
            log.error("Unexpected node error: nodeId={}", nodeId, e);
            return NodeExecutionResult.builder().nodeId(nodeId).status("FAILED")
                    .errorMessage("Unexpected: " + e.getMessage()).durationMs(System.currentTimeMillis() - startTime).build();
        }
    }

    /** 子类实现具体业务逻辑 */
    protected abstract Map<String, Object> doExecute(NodeExecutionContext context) throws NodeExecutionException;

    // ---- 配置读取工具方法 ----
    protected String getConfigString(JsonNode config, String key) {
        return config != null && config.has(key) ? config.path(key).asText() : null;
    }
    protected Integer getConfigInt(JsonNode config, String key) {
        return config != null && config.has(key) ? config.path(key).asInt() : null;
    }
    protected Long getConfigLong(JsonNode config, String key) {
        return config != null && config.has(key) ? config.path(key).asLong() : null;
    }
    protected Boolean getConfigBoolean(JsonNode config, String key) {
        return config != null && config.has(key) ? config.path(key).asBoolean() : null;
    }

    @SuppressWarnings("unchecked")
    protected <T> T getInput(Map<String, Object> inputs, String key, Class<T> type) {
        Object value = inputs != null ? inputs.get(key) : null;
        if (value == null) return null;
        return type.isInstance(value) ? type.cast(value) : null;
    }
    @SuppressWarnings("unchecked")
    protected List<String> getInputStringList(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        if (value instanceof List) return ((List<?>) value).stream().map(String::valueOf).toList();
        return Collections.emptyList();
    }
}
