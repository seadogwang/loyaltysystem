package com.loyalty.platform.api.service;

import com.loyalty.platform.domain.entity.ProgramSchema;
import com.loyalty.platform.domain.entity.RuleDefinition;
import com.loyalty.platform.domain.repository.ProgramSchemaRepository;
import com.loyalty.platform.domain.repository.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProgramSchemaService {

    private static final Logger log = LoggerFactory.getLogger(ProgramSchemaService.class);

    private final ProgramSchemaRepository schemaRepo;
    private final RuleDefinitionRepository ruleRepo;

    public ProgramSchemaService(ProgramSchemaRepository schemaRepo, RuleDefinitionRepository ruleRepo) {
        this.schemaRepo = schemaRepo;
        this.ruleRepo = ruleRepo;
    }

    public Map<String, Object> getCurrentSchema(String programCode, String entityType) {
        return schemaRepo.findCurrentByType(programCode, entityType)
                .map(ProgramSchema::getFieldSchema)
                .orElse(Collections.emptyMap());
    }

    public String getCurrentVersion(String programCode, String entityType) {
        return schemaRepo.findCurrentByType(programCode, entityType)
                .map(ProgramSchema::getVersionTag)
                .orElse("v0");
    }

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

    public void injectSchemaVersion(Map<String, Object> extAttributes, String programCode, String entityType) {
        if (extAttributes == null) return;
        String version = getCurrentVersion(programCode, entityType);
        try {
            extAttributes.put("_schema_version", version);
        } catch (UnsupportedOperationException e) {
            log.warn("[ProgramSchema] Cannot inject _schema_version into immutable map");
        }
    }

    public ProgramSchema saveSchema(String programCode, String entityType, Map<String, Object> fieldSchema) {
        ProgramSchema existing = schemaRepo.findCurrentByType(programCode, entityType).orElse(null);
        int nextVersion = existing != null ? Integer.parseInt(existing.getVersion().replace("v", "")) + 1 : 1;

        ProgramSchema ps = ProgramSchema.builder()
                .programCode(programCode)
                .entityType(entityType.toUpperCase())
                .entityCategory("SYSTEM")
                .version("v" + nextVersion)
                .status("DRAFT")
                .fieldSchema(fieldSchema)
                .schemaCode(entityType.toLowerCase())
                .build();
        return schemaRepo.save(ps);
    }

    public ProgramSchema publishSchema(String programCode, String entityType, Map<String, Object> fieldSchema) {
        ProgramSchema ps = saveSchema(programCode, entityType, fieldSchema);
        ps.setStatus("PUBLISHED");
        ps.setPublishedAt(java.time.LocalDateTime.now());
        return schemaRepo.save(ps);
    }

    /** 查询所有 Schema */
    public List<ProgramSchema> listSchemas(String programCode, String category) {
        if (category != null && !category.isBlank()) {
            return schemaRepo.findByProgramCodeAndEntityCategory(programCode, category);
        }
        return schemaRepo.findByProgramCodeAndCategory(programCode, null);
    }

    /** 按 entityType 查询 */
    public Optional<ProgramSchema> getByEntityType(String programCode, String entityType) {
        return schemaRepo.findByProgramCodeAndEntityType(programCode, entityType);
    }

    /** 删除 Schema */
    public void deleteSchema(Long id) {
        schemaRepo.deleteById(id);
    }
}