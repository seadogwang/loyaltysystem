package com.loyalty.platform.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.api.dto.MappingRuleDto;
import com.loyalty.platform.api.dto.TestMappingRequestDto;
import com.loyalty.platform.api.service.MappingService;
import com.loyalty.platform.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 渠道适配器映射配置 API。
 *
 * <p>提供入站/出站字段映射规则的 CRUD 及测试执行功能。
 * 映射定义后端可视化配置工具 (ChartDB) 中的 API ↔ Business 映射关系。
 *
 * <p>Program Code 解析优先级:
 * <ol>
 *   <li>请求头 {@code X-Program-Code}</li>
 *   <li>{@code DEFAULT} 兜底值</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/channels")
public class MappingController {

    private final MappingService mappingService;
    private final ObjectMapper objectMapper;

    public MappingController(MappingService mappingService, ObjectMapper objectMapper) {
        this.mappingService = mappingService;
        this.objectMapper = objectMapper;
    }

    // ======================== 入站映射 ========================

    /**
     * 获取入站映射规则列表。
     *
     * @param channel       渠道标识 (tmall, jd 等)
     * @param operationCode 操作码 (orderCreate, refundCreate 等)
     * @param programCode   计划代码 (请求头或查询参数)
     * @return 映射规则列表
     */
    @GetMapping("/{channel}/inbound-mappings/{operationCode}")
    public ResponseEntity<ApiResponse<List<MappingRuleDto>>> getInboundMappings(
            @PathVariable String channel,
            @PathVariable String operationCode,
            @RequestParam(name = "programCode", required = false) String programCode,
            @RequestHeader(name = "X-Program-Code", required = false) String headerProgramCode) {
        String pc = resolveProgramCode(programCode, headerProgramCode);
        List<MappingRuleDto> rules = mappingService.getInboundMappings(pc, channel, operationCode);
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    /**
     * 保存入站映射规则列表。
     *
     * @param channel       渠道标识
     * @param operationCode 操作码
     * @param programCode   计划代码
     * @param rules         映射规则列表
     * @return 空响应
     */
    @PutMapping("/{channel}/inbound-mappings/{operationCode}")
    public ResponseEntity<ApiResponse<Void>> saveInboundMappings(
            @PathVariable String channel,
            @PathVariable String operationCode,
            @RequestParam(name = "programCode", required = false) String programCode,
            @RequestHeader(name = "X-Program-Code", required = false) String headerProgramCode,
            @RequestBody List<MappingRuleDto> rules) {
        String pc = resolveProgramCode(programCode, headerProgramCode);
        mappingService.saveInboundMappings(pc, channel, operationCode, rules);
        return ResponseEntity.ok(ApiResponse.success("入站映射保存成功", null));
    }

    // ======================== 出站映射 ========================

    /**
     * 获取出站映射规则列表。
     */
    @GetMapping("/{channel}/outbound-mappings/{operationCode}")
    public ResponseEntity<ApiResponse<List<MappingRuleDto>>> getOutboundMappings(
            @PathVariable String channel,
            @PathVariable String operationCode,
            @RequestParam(name = "programCode", required = false) String programCode,
            @RequestHeader(name = "X-Program-Code", required = false) String headerProgramCode) {
        String pc = resolveProgramCode(programCode, headerProgramCode);
        List<MappingRuleDto> rules = mappingService.getOutboundMappings(pc, channel, operationCode);
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    /**
     * 保存出站映射规则列表。
     */
    @PutMapping("/{channel}/outbound-mappings/{operationCode}")
    public ResponseEntity<ApiResponse<Void>> saveOutboundMappings(
            @PathVariable String channel,
            @PathVariable String operationCode,
            @RequestParam(name = "programCode", required = false) String programCode,
            @RequestHeader(name = "X-Program-Code", required = false) String headerProgramCode,
            @RequestBody List<MappingRuleDto> rules) {
        String pc = resolveProgramCode(programCode, headerProgramCode);
        mappingService.saveOutboundMappings(pc, channel, operationCode, rules);
        return ResponseEntity.ok(ApiResponse.success("出站映射保存成功", null));
    }

    // ======================== 测试映射 ========================

    /**
     * 测试执行映射规则。
     *
     * <p>请求体示例:
     * <pre>
     * {
     *   "sourceJson": "{\"tid\": \"12345\", \"payment\": \"99.90\"}",
     *   "mappings": [ ... ]
     * }
     * </pre>
     */
    @PostMapping("/{channel}/test-mapping")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testMapping(
            @PathVariable String channel,
            @RequestBody TestMappingRequestDto request) {
        if (request.getSourceJson() == null || request.getSourceJson().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("ERR_INVALID_PARAM", "sourceJson is required"));
        }
        List<MappingRuleDto> mappings = request.getMappings() != null
                ? request.getMappings()
                : List.of();

        Map<String, Object> result = mappingService.testMapping(request.getSourceJson(), mappings);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ======================== 辅助方法 ========================

    /**
     * 解析 Program Code: 优先使用查询参数, 其次 Header, 最后 DEFAULT。
     */
    private String resolveProgramCode(String queryParam, String headerParam) {
        if (queryParam != null && !queryParam.isBlank()) {
            return queryParam.trim();
        }
        if (headerParam != null && !headerParam.isBlank()) {
            return headerParam.trim();
        }
        return "DEFAULT";
    }
}
