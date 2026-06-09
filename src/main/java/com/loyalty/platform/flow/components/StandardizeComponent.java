package com.loyalty.platform.flow.components;

import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.loyalty.platform.mapping.ScriptingTransformer;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * 数据标准化组件 — 将渠道原始 payload 转换为标准格式。
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>SCRIPT 模式：通过 GraalVM JavaScript 沙箱执行转换脚本</li>
 *   <li>PASSTHROUGH 模式：直接使用原始 payload</li>
 * </ul>
 */
@LiteflowComponent("standardizeCmp")
public class StandardizeComponent extends BaseLiteflowComponent {

    @Autowired
    private ScriptingTransformer scriptingTransformer;

    @Override
    @SuppressWarnings("unchecked")
    protected void doProcess(EventContext ctx) throws Exception {
        String rawPayload = ctx.getRawPayload();
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("[Standardize] rawPayload 为空，使用空 Map");
            ctx.setStandardizedPayload(Map.of());
            return;
        }

        // 尝试 JSON 解析 → 如果是简单 JSON 直接使用
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(rawPayload, Map.class);
            ctx.setStandardizedPayload(parsed);
            log.debug("[Standardize] 直接解析: {} fields", parsed.size());
        } catch (Exception e) {
            // 无法解析则保留原始字符串
            ctx.setStandardizedPayload(Map.of("_raw", rawPayload));
            log.warn("[Standardize] JSON 解析失败: {}", e.getMessage());
        }
    }
}