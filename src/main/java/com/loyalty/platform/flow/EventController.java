package com.loyalty.platform.flow;

import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.common.context.TenantContext;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件处理流程控制器 — 通过 LiteFlow FlowExecutor 执行组件链。
 */
@Tag(name = "Flow Execution", description = "LiteFlow 流程执行 — 事件处理链触发与测试运行")
@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final FlowExecutor flowExecutor;

    public EventController(FlowExecutor flowExecutor) {
        this.flowExecutor = flowExecutor;
    }

    @Operation(summary = "执行事件流程", description = "通过 LiteFlow 链执行事件处理流程")
    @PostMapping("/{chainName}/{programCode}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processEvent(
            @Parameter(description = "流程链名称（如 ORDER_CHAIN）") @PathVariable String chainName,
            @Parameter(description = "租户代码") @PathVariable String programCode,
            @Parameter(description = "原始 JSON payload") @RequestBody String body) {

        TenantContext.set(programCode);

        try {
            EventContext ctx = new EventContext();
            ctx.setProgramCode(programCode);
            ctx.setChannel(extractChannel(body));
            ctx.setRawPayload(body);
            ctx.setIdempotencyKey(generateIdempotencyKey(chainName, programCode, body));

            LiteflowResponse response = flowExecutor.execute2Resp(chainName, null, ctx);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chainName", chainName);
            result.put("success", response.isSuccess());
            result.put("message", response.getMessage());
            result.put("actionCount", ctx.getActions() != null ? ctx.getActions().size() : 0);
            result.put("processingFailed", ctx.isProcessingFailed());

            if (!response.isSuccess()) {
                log.error("[EventController] 链执行失败: chain={}, err={}", chainName, response.getMessage());
                return ResponseEntity.ok(ApiResponse.error("ERR_FLOW_FAILED",
                        response.getMessage() != null ? response.getMessage() : "流程执行失败"));
            }

            log.info("[EventController] 链执行成功: chain={}, actions={}", chainName,
                    ctx.getActions() != null ? ctx.getActions().size() : 0);
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("[EventController] 异常: chain={}", chainName, e);
            return ResponseEntity.ok(ApiResponse.error("ERR_FLOW_EXCEPTION", e.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    @Operation(summary = "测试运行", description = "用于流程设计器的测试功能")
    @PostMapping("/test-run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testRun(
            @RequestBody Map<String, Object> body) {
        String pc = TenantContext.getRequired();
        String chainName = (String) body.getOrDefault("chainName", "ORDER_CHAIN");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.getOrDefault("payload", Map.of());

        String rawJson;
        try {
            rawJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            rawJson = payload.toString();
        }

        EventContext ctx = new EventContext();
        ctx.setProgramCode(pc);
        ctx.setChannel((String) payload.getOrDefault("channel", "TEST"));
        ctx.setRawPayload(rawJson);
        ctx.setIdempotencyKey("test-" + System.currentTimeMillis());

        try {
            LiteflowResponse response = flowExecutor.execute2Resp(chainName, null, ctx);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chainName", chainName);
            result.put("success", response.isSuccess());
            result.put("message", response.getMessage());
            result.put("actionCount", ctx.getActions() != null ? ctx.getActions().size() : 0);
            result.put("actions", ctx.getActions() != null ? ctx.getActions().stream()
                    .map(a -> Map.of("type", a.actionType(), "summary", a.toString()))
                    .toList() : java.util.List.of());

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("ERR_TEST_FAILED", e.getMessage()));
        }
    }

    private String extractChannel(String body) {
        try {
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
            return (String) map.getOrDefault("channel", "UNKNOWN");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String generateIdempotencyKey(String chainName, String programCode, String body) {
        String hash = Integer.toHexString(body.hashCode());
        return programCode + ":" + chainName + ":" + hash + ":" + System.currentTimeMillis();
    }
}
