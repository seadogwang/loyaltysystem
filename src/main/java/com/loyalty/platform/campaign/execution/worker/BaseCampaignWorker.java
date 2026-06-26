package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Zeebe Worker 基类 — 所有 Campaign Worker 继承此类。
 *
 * <p>提供变量提取、干预检查、统一日志等基础能力。
 * 开发阶段：模拟 Zeebe JobWorker 行为。
 * 生产阶段：使用 {@code @JobWorker(type = "...")} 注解。
 */
public abstract class BaseCampaignWorker {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final InterventionService interventionService;

    protected BaseCampaignWorker(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    /**
     * 获取 Worker 处理的 Job 类型。
     */
    public abstract String getJobType();

    /**
     * 执行 Worker 业务逻辑。
     */
    public abstract Map<String, Object> handle(Map<String, Object> variables);

    /**
     * 执行前检查（暂停/跳过/限流防护）。
     */
    protected void preCheck(String planId, String nodeId, String tenantId) {
        interventionService.checkBeforeExecution(planId, nodeId, tenantId);
    }

    /**
     * 安全获取变量值。
     */
    protected String getString(Map<String, Object> variables, String key) {
        Object val = variables.get(key);
        return val != null ? val.toString() : null;
    }

    protected Integer getInt(Map<String, Object> variables, String key) {
        Object val = variables.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return null;
    }

    protected Double getDouble(Map<String, Object> variables, String key) {
        Object val = variables.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return null;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> getMap(Map<String, Object> variables, String key) {
        Object val = variables.get(key);
        return val instanceof Map ? (Map<String, Object>) val : null;
    }

    /**
     * 构建标准执行结果。
     */
    protected Map<String, Object> result(String key, Object value) {
        return Map.of(key, value, "status", "COMPLETED");
    }

    protected Map<String, Object> errorResult(String message) {
        return Map.of("status", "FAILED", "error", message);
    }
}
