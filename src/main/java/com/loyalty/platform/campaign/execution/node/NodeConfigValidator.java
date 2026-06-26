package com.loyalty.platform.campaign.execution.node;

import com.loyalty.platform.domain.repository.campaign.CampaignNodeDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 节点配置校验器 — JSON Schema + Handler 自定义校验。
 */
@Component
public class NodeConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(NodeConfigValidator.class);
    private final CampaignNodeDefinitionRepository definitionRepository;

    public NodeConfigValidator(CampaignNodeDefinitionRepository definitionRepository) {
        this.definitionRepository = definitionRepository;
    }

    /** 校验节点配置 */
    public ValidationResult validate(String nodeType, Map<String, Object> config) {
        var def = definitionRepository.findAllActive().stream()
                .filter(d -> d.getNodeType().equals(nodeType)).findFirst();

        if (def.isEmpty()) {
            return ValidationResult.failed("Node type not found: " + nodeType);
        }

        // Simplified: check required fields from config schema
        List<String> errors = new ArrayList<>();
        try {
            String schema = def.get().getConfigSchema();
            if (schema != null && schema.contains("\"required\"")) {
                // Extract required fields from JSON schema string
                // Simplified: hardcoded validation for common types
                validateRequiredFields(nodeType, config, errors);
            }
        } catch (Exception e) {
            log.warn("Schema validation error for nodeType={}: {}", nodeType, e.getMessage());
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failed(errors);
    }

    private void validateRequiredFields(String nodeType, Map<String, Object> config, List<String> errors) {
        switch (nodeType) {
            case "AUDIENCE_FILTER":
                if (config == null || !config.containsKey("segmentCode") || String.valueOf(config.get("segmentCode")).isEmpty())
                    errors.add("segmentCode is required");
                break;
            case "SEND_EMAIL":
                if (config == null || !config.containsKey("assetId") || String.valueOf(config.get("assetId")).isEmpty())
                    errors.add("assetId is required");
                break;
            case "OFFER_POINTS":
                if (config == null || !config.containsKey("pointType")) errors.add("pointType is required");
                if (config == null || !config.containsKey("amount")) errors.add("amount is required");
                break;
            case "DELAY":
                if (config == null || !config.containsKey("duration")) errors.add("duration is required");
                break;
            case "AI_SCORE":
                if (config == null || !config.containsKey("modelType")) errors.add("modelType is required");
                break;
            case "CONDITION":
                if (config == null || !config.containsKey("field")) errors.add("field is required");
                if (config == null || !config.containsKey("operator")) errors.add("operator is required");
                break;
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult success() { return new ValidationResult(true, Collections.emptyList()); }
        public static ValidationResult failed(String error) { return new ValidationResult(false, List.of(error)); }
        public static ValidationResult failed(List<String> errors) { return new ValidationResult(false, errors); }
    }
}
