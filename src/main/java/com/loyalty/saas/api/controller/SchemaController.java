package com.loyalty.saas.api.controller;

import com.loyalty.saas.api.service.SchemaService;
import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.common.dto.ApiResponse;
import com.loyalty.saas.domain.entity.RuleDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema API — 供前端管理后台使用。
 *
 * <p>端点：
 * <ul>
 *   <li>GET /api/schemas/{entityType} — 获取当前 Program 的 JSON Schema</li>
 *   <li>GET /api/schemas/{entityType}/deprecation-check?field={fieldName} — 废弃字段前检查 DRL 引用</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/schemas")
public class SchemaController {

    private final SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /** 获取当前 Program 的生效 JSON Schema */
    @GetMapping("/{entityType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchema(@PathVariable String entityType) {
        String programCode = TenantContext.getRequired();
        Map<String, Object> schema = schemaService.getCurrentSchema(programCode, entityType.toUpperCase());
        String version = schemaService.getCurrentVersion(programCode, entityType.toUpperCase());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", schema);
        result.put("version", version);
        result.put("entity_type", entityType.toUpperCase());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 废弃字段前的拦截校验 —— 检查是否被 DRL 规则引用 */
    @GetMapping("/{entityType}/deprecation-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFieldDeprecation(
            @PathVariable String entityType,
            @RequestParam String field) {
        String programCode = TenantContext.getRequired();
        List<RuleDefinition> refs = schemaService.getFieldRuleReferences(programCode, field);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("safe_to_deprecate", refs.isEmpty());
        result.put("referencing_rules", refs.stream().map(r -> Map.of(
                "rule_code", r.getRuleCode(),
                "rule_name", r.getRuleName(),
                "version", r.getVersion()
        )).collect(Collectors.toList()));

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}