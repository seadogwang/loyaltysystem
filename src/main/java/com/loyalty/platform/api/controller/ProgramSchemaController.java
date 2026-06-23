package com.loyalty.platform.api.controller;

import com.loyalty.platform.api.service.ProgramSchemaService;
import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.RuleDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Program Schema", description = "Program Schema management — JSON Schema definitions and deprecation checks")
@RestController
@RequestMapping("/api/schemas")
public class ProgramSchemaController {

    private final ProgramSchemaService schemaService;

    public ProgramSchemaController(ProgramSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Operation(summary = "Get Schema")
    @GetMapping("/{entityType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchema(
            @Parameter(description = "Entity type") @PathVariable String entityType) {
        String programCode = TenantContext.getRequired();
        Map<String, Object> schema = schemaService.getCurrentSchema(programCode, entityType.toUpperCase());
        String version = schemaService.getCurrentVersion(programCode, entityType.toUpperCase());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", schema);
        result.put("version", version);
        result.put("entity_type", entityType.toUpperCase());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Check field deprecation")
    @GetMapping("/{entityType}/deprecation-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFieldDeprecation(
            @Parameter(description = "Entity type") @PathVariable String entityType,
            @Parameter(description = "Field name") @RequestParam String field) {
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