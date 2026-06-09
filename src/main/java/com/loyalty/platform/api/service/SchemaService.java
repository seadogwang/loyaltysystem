package com.loyalty.platform.api.service;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.domain.entity.RuleDefinition;
import com.loyalty.platform.domain.entity.SchemaVersion;
import com.loyalty.platform.domain.repository.RuleDefinitionRepository;
import com.loyalty.platform.domain.repository.SchemaVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Schema 服务 — 管理 JSON Schema 版本，提供废弃字段影响分析。
 *
 * <p>核心功能：
 * <ul>
 *   <li>获取当前 Program 某实体类型的生效 JSON Schema</li>
 *   <li>废弃字段前检查是否被 DRL 规则引用</li>
 *   <li>Schema 版本号用于会员数据的双写（独立字段 + JSONB 内部 _schema_version）</li>
 * </ul>
 */
@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    private final SchemaVersionRepository schemaRepo;
    private final RuleDefinitionRepository ruleRepo;

    public SchemaService(SchemaVersionRepository schemaRepo, RuleDefinitionRepository ruleRepo) {
        this.schemaRepo = schemaRepo;
        this.ruleRepo = ruleRepo;
    }

    /**
     * 获取当前 Program 指定类型的生效 JSON Schema。
     *
     * @param programCode 租户代码
     * @param schemaType  Schema 类型（MEMBER / TRANSACTION 等）
     * @return JSON Schema Map，若没有则返回空 Map
     */
    public Map<String, Object> getCurrentSchema(String programCode, String schemaType) {
        return schemaRepo.findCurrentByType(programCode, schemaType)
                .map(SchemaVersion::getSchemaJson)
                .orElse(Collections.emptyMap());
    }

    /**
     * 获取当前 Schema 版本号标签。
     */
    public String getCurrentVersion(String programCode, String schemaType) {
        return schemaRepo.findCurrentByType(programCode, schemaType)
                .map(SchemaVersion::getVersionTag)
                .orElse("v0.0.0");
    }

    /**
     * 废弃字段前的拦截校验——检查该字段是否被 DRL 规则引用。
     *
     * <p>扫描当前 Program 所有 ACTIVE 规则的 DRL 内容，
     * 查找是否包含对目标字段的引用（如 getExtString("field_name") 或 getExtNumber("field_name")）。
     *
     * @param programCode 租户代码
     * @param fieldName   拟废弃的字段名
     * @return 引用该字段的规则列表（空列表表示无引用，可安全废弃）
     */
    public List<RuleDefinition> getFieldRuleReferences(String programCode, String fieldName) {
        List<RuleDefinition> activeRules = ruleRepo.findActiveByProgramCode(programCode);
        List<RuleDefinition> referencingRules = new ArrayList<>();

        for (RuleDefinition rule : activeRules) {
            String drl = rule.getDrlContent();
            if (drl != null && drl.contains("\"" + fieldName + "\"")) {
                referencingRules.add(rule);
            }
        }
        return referencingRules;
    }

    /**
     * 注入 _schema_version 到 ext_attributes JSON 根节点。
     *
     * @param extAttributes 会员扩展属性
     * @param programCode   租户代码
     * @param schemaType    Schema 类型
     */
    public void injectSchemaVersion(Map<String, Object> extAttributes, String programCode, String schemaType) {
        if (extAttributes == null) return;
        String version = getCurrentVersion(programCode, schemaType);
        try {
            extAttributes.put("_schema_version", version);
        } catch (UnsupportedOperationException e) {
            log.warn("[SchemaService] Cannot inject _schema_version into immutable map");
        }
    }
}