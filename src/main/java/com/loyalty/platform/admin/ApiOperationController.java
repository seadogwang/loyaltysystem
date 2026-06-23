package com.loyalty.platform.admin;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.ApiOperationMetadata;
import com.loyalty.platform.domain.entity.ProgramSchema;
import com.loyalty.platform.domain.repository.ApiOperationMetadataRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API 操作元数据管理 Controller — 提供 API 操作的 CRUD 及业务实体关联。
 *
 * <p>对应前端的 API 配置管理页面，参考 Apifox 的 API 文档管理功能设计。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/api-operations")
public class ApiOperationController {

    private static final Logger log = LoggerFactory.getLogger(ApiOperationController.class);

    @PersistenceContext
    private EntityManager em;

    private final ApiOperationMetadataRepository apiOperationRepo;

    /** 预定义的业务实体类型列表 */
    private static final List<String> PREDEFINED_ENTITY_TYPES = List.of(
            "MEMBER", "ORDER", "OrderItem", "TRANSACTION_EVENT", "BEHAVIOR"
    );

    public ApiOperationController(ApiOperationMetadataRepository apiOperationRepo) {
        this.apiOperationRepo = apiOperationRepo;
    }

    // ==================== 列表查询 ====================

    /**
     * API 操作列表 — 支持按 channel / direction / httpMethod 过滤
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String httpMethod) {
        String pc = TenantContext.getRequired();

        // 构建 JPQL 动态查询
        StringBuilder jpql = new StringBuilder(
                "SELECT a FROM ApiOperationMetadata a WHERE a.programCode = :pc");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pc", pc);

        if (channel != null && !channel.isBlank()) {
            jpql.append(" AND a.channel = :channel");
            params.put("channel", channel);
        }
        if (direction != null && !direction.isBlank()) {
            jpql.append(" AND a.direction = :direction");
            params.put("direction", direction);
        }
        if (httpMethod != null && !httpMethod.isBlank()) {
            jpql.append(" AND a.httpMethod = :httpMethod");
            params.put("httpMethod", httpMethod);
        }
        jpql.append(" ORDER BY a.channel, a.operationCode");

        var query = em.createQuery(jpql.toString(), ApiOperationMetadata.class);
        params.forEach(query::setParameter);

        List<Map<String, Object>> result = query.getResultList().stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 单条 API 操作详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable Long id) {
        return apiOperationRepo.findById(id)
                .map(a -> ResponseEntity.ok(ApiResponse.success(toMap(a))))
                .orElse(ResponseEntity.ok(ApiResponse.error("ERR_NOT_FOUND", "API 操作不存在")));
    }

    // ==================== 创建 ====================

    /**
     * 创建 API 操作
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        String pc = TenantContext.getRequired();

        String operationCode = (String) body.get("operation_code");
        String channel = (String) body.get("channel");
        String direction = (String) body.get("direction");
        String httpMethod = (String) body.get("http_method");
        String httpPath = (String) body.get("http_path");

        if (operationCode == null || operationCode.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "operation_code 不能为空"));
        }
        if (channel == null || channel.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "channel 不能为空"));
        }
        if (direction == null || direction.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "direction 不能为空"));
        }
        if (httpMethod == null || httpMethod.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "http_method 不能为空"));
        }
        if (httpPath == null || httpPath.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "http_path 不能为空"));
        }

        // 检查唯一约束 (programCode, channel, operationCode)
        if (apiOperationRepo.findByProgramCodeAndChannelAndOperationCode(pc, channel, operationCode).isPresent()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_DUPLICATE",
                    "该渠道下操作编码已存在: " + operationCode));
        }

        ApiOperationMetadata entity = ApiOperationMetadata.builder()
                .programCode(pc)
                .channel(channel)
                .operationCode(operationCode)
                .operationName((String) body.get("operation_name"))
                .direction(direction)
                .httpMethod(httpMethod)
                .httpPath(httpPath)
                .authType((String) body.getOrDefault("auth_type", "NONE"))
                .paginationType((String) body.getOrDefault("pagination_type", "NONE"))
                .targetBusinessEntity((String) body.get("target_business_entity"))
                .sourceBusinessEntity((String) body.get("source_business_entity"))
                .apiEntityType((String) body.get("api_entity_type"))
                .build();

        // auth_config (JSONB)
        if (body.containsKey("auth_config") && body.get("auth_config") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> authConfig = (Map<String, Object>) body.get("auth_config");
            entity.setAuthConfig(authConfig);
        }

        apiOperationRepo.save(entity);
        log.info("[ApiOperation] 创建: channel={}, operationCode={}, method={} {}",
                channel, operationCode, httpMethod, httpPath);

        return ResponseEntity.ok(ApiResponse.success(toMap(entity)));
    }

    // ==================== 更新 ====================

    /**
     * 更新 API 操作
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String pc = TenantContext.getRequired();

        var entity = apiOperationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("API 操作不存在"));
        if (!entity.getProgramCode().equals(pc)) {
            return ResponseEntity.ok(ApiResponse.error("ERR_FORBIDDEN", "无权操作此记录"));
        }

        if (body.containsKey("operation_name")) {
            entity.setOperationName((String) body.get("operation_name"));
        }
        if (body.containsKey("direction")) {
            entity.setDirection((String) body.get("direction"));
        }
        if (body.containsKey("http_method")) {
            entity.setHttpMethod((String) body.get("http_method"));
        }
        if (body.containsKey("http_path")) {
            entity.setHttpPath((String) body.get("http_path"));
        }
        if (body.containsKey("auth_type")) {
            entity.setAuthType((String) body.get("auth_type"));
        }
        if (body.containsKey("pagination_type")) {
            entity.setPaginationType((String) body.get("pagination_type"));
        }
        if (body.containsKey("target_business_entity")) {
            entity.setTargetBusinessEntity((String) body.get("target_business_entity"));
        }
        if (body.containsKey("source_business_entity")) {
            entity.setSourceBusinessEntity((String) body.get("source_business_entity"));
        }
        if (body.containsKey("api_entity_type")) {
            entity.setApiEntityType((String) body.get("api_entity_type"));
        }
        if (body.containsKey("auth_config") && body.get("auth_config") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> authConfig = (Map<String, Object>) body.get("auth_config");
            entity.setAuthConfig(authConfig);
        }

        apiOperationRepo.save(entity);
        log.info("[ApiOperation] 更新: id={}", id);

        return ResponseEntity.ok(ApiResponse.success(toMap(entity)));
    }

    // ==================== 删除 ====================

    /**
     * 删除 API 操作
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable Long id) {
        String pc = TenantContext.getRequired();

        var entity = apiOperationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("API 操作不存在"));
        if (!entity.getProgramCode().equals(pc)) {
            return ResponseEntity.ok(ApiResponse.error("ERR_FORBIDDEN", "无权操作此记录"));
        }

        apiOperationRepo.delete(entity);
        log.info("[ApiOperation] 删除: id={}, operationCode={}", id, entity.getOperationCode());
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "deleted", true)));
    }

    // ==================== 辅助接口 ====================

    /**
     * 获取可选的业务实体类型列表。
     * <p>从 SchemaVersion 表读取已定义 schema_type，合并预定义类型后返回。
     */
    @GetMapping("/entity-types")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEntityTypes() {
        String pc = TenantContext.getRequired();

        // 从 ProgramSchema 表读取已定义的 entity_type
        List<String> dbTypes = em.createQuery(
                        "SELECT DISTINCT s.entityType FROM ProgramSchema s WHERE s.programCode = :pc",
                        String.class)
                .setParameter("pc", pc)
                .getResultList();

        // 合并预定义类型并去重
        Set<String> allTypes = new LinkedHashSet<>(PREDEFINED_ENTITY_TYPES);
        allTypes.addAll(dbTypes);

        List<Map<String, Object>> result = allTypes.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entityType", t);
            m.put("label", t);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 查询时可用的渠道列表（从已有 API 操作中取 distinct channel）
     */
    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<String>>> getChannels() {
        String pc = TenantContext.getRequired();
        List<String> channels = em.createQuery(
                        "SELECT DISTINCT a.channel FROM ApiOperationMetadata a WHERE a.programCode = :pc ORDER BY a.channel",
                        String.class)
                .setParameter("pc", pc)
                .getResultList();
        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    // ==================== 私有工具方法 ====================

    private Map<String, Object> toMap(ApiOperationMetadata a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("program_code", a.getProgramCode());
        m.put("channel", a.getChannel());
        m.put("operation_code", a.getOperationCode());
        m.put("operation_name", a.getOperationName());
        m.put("direction", a.getDirection());
        m.put("http_method", a.getHttpMethod());
        m.put("http_path", a.getHttpPath());
        m.put("auth_type", a.getAuthType());
        m.put("auth_config", a.getAuthConfig());
        m.put("pagination_type", a.getPaginationType());
        m.put("target_business_entity", a.getTargetBusinessEntity());
        m.put("source_business_entity", a.getSourceBusinessEntity());
        m.put("api_entity_type", a.getApiEntityType());
        m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        return m;
    }
}
