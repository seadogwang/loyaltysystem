package com.loyalty.platform.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.domain.entity.RuleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DRL generator for promo (ACTIVITY_PROMO) rules.
 */
@Component
public class UnifiedPromoDrlGenerator {

    private static final Logger log = LoggerFactory.getLogger(UnifiedPromoDrlGenerator.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public String generate(RuleDefinition rule) {
        Map<String, Object> meta = rule.getMetadata();
        if (meta == null) meta = Map.of();
        String ruleCode = rule.getRuleCode();
        String ruleGroup = rule.getRuleGroup();
        int priority = rule.getPriority() != null ? rule.getPriority() : 0;

        StringBuilder drl = new StringBuilder();
        drl.append("package com.loyalty.platform.rules;\n");
        drl.append("import com.loyalty.platform.rules.drl.MemberFact;\n");
        drl.append("import com.loyalty.platform.rules.drl.EventFact;\n");
        drl.append("import com.loyalty.platform.rules.action.ActionCollector;\n");
        drl.append("\n");
        drl.append("rule \"").append(ruleCode).append("\"\n");
        if (ruleGroup != null && !ruleGroup.isBlank()) {
            drl.append("    ruleflow-group \"").append(ruleGroup).append("\"\n");
        }
        drl.append("    salience ").append((priority)).append("\n");
        drl.append("when\n");
        drl.append("    $event: EventFact()\n");
        drl.append("    $member: MemberFact(memberId == $event.memberId)\n");

        // Generate conditions
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) meta.getOrDefault("entity_conditions", List.of());
        for (Map<String, Object> cond : conditions) {
            String entity = (String) cond.get("entity");
            String attr = (String) cond.get("attribute");
            String operator = (String) cond.get("operator");
            Object value = cond.get("value");

            if ("Order".equals(entity) && "total_amount".equals(attr)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> range = (Map<String, Object>) value;
                if (range != null) {
                    Object min = range.get("min");
                    Object max = range.get("max");
                    if (min instanceof Number) {
                        drl.append("    eval($event.getPayloadNumber(\"total_amount\").compareTo(new java.math.BigDecimal(\"").append(min).append("\")) >= 0)\n");
                    }
                    if (max instanceof Number) {
                        drl.append("    eval($event.getPayloadNumber(\"total_amount\").compareTo(new java.math.BigDecimal(\"").append(max).append("\")) <= 0)\n");
                    }
                }
            } else if ("Member".equals(entity) && "tier_code".equals(attr) && value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> tiers = (List<String>) value;
                String joined = tiers.stream().map(t -> "$member.getTierCode().equals(\"" + t + "\")").collect(Collectors.joining(" || "));
                drl.append("    eval(").append(joined).append(")\n");
            }
        }

        drl.append("    $orderAmount: java.math.BigDecimal($event.getPayloadNumber(\"total_amount\"))\n");
        drl.append("then\n");

        // Reward steps
        @SuppressWarnings("unchecked")
        Map<String, Object> reward = (Map<String, Object>) meta.getOrDefault("reward", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) reward.getOrDefault("steps", List.of());

        if (steps.isEmpty()) {
            drl.append("    // No steps configured — default: 1x multiplier\n");
            drl.append("    java.math.BigDecimal _pts = $orderAmount;\n");
        } else {
            drl.append("    java.math.BigDecimal _pts = java.math.BigDecimal.ZERO;\n");
            drl.append("    java.math.BigDecimal _remaining = $orderAmount;\n");

            String cycleMode = (String) reward.getOrDefault("cycleMode", "SINGLE_MATCH");
            @SuppressWarnings("unchecked")
            List<Number> cycleThresholds = (List<Number>) reward.getOrDefault("cycleThresholdOrder", List.of());

            if ("THRESHOLD_LOOP".equals(cycleMode) && !cycleThresholds.isEmpty()) {
                BigDecimal highest = new BigDecimal(cycleThresholds.get(0).toString());
                drl.append("    while (_remaining.compareTo(new java.math.BigDecimal(\"").append(highest).append("\")) >= 0) {\n");
                // Find multiplier for highest threshold
                for (Map<String, Object> step : steps) {
                    if (Boolean.TRUE.equals(step.get("isCycleThreshold"))) {
                        drl.append("        _pts = _pts.add(new java.math.BigDecimal(\"").append(highest).append("\").multiply(new java.math.BigDecimal(\"").append(step.get("multiplier")).append("\")));\n");
                        break;
                    }
                }
                drl.append("        _remaining = _remaining.subtract(new java.math.BigDecimal(\"").append(highest).append("\"));\n");
                drl.append("    }\n");
            }

            // Remaining amount — find matching step
            for (Map<String, Object> step : steps) {
                Object lower = step.get("lower");
                Object upper = step.get("upper");
                Object mult = step.get("multiplier");
                String cond = "_remaining.compareTo(new java.math.BigDecimal(\"" + lower + "\")) >= 0";
                if (upper != null && !String.valueOf(upper).isBlank()) {
                    cond += " && _remaining.compareTo(new java.math.BigDecimal(\"" + upper + "\")) < 0";
                }
                drl.append("    if (").append(cond).append(") {\n");
                drl.append("        _pts = _pts.add(_remaining.multiply(new java.math.BigDecimal(\"").append(mult).append("\")));\n");
                drl.append("    }\n");
            }
        }

        // Per-order limit
        Object perOrderLimit = reward.get("perOrderLimit");
        if (perOrderLimit instanceof Number) {
            drl.append("    if (_pts.compareTo(new java.math.BigDecimal(\"").append(perOrderLimit).append("\")) > 0) {\n");
            drl.append("        _pts = new java.math.BigDecimal(\"").append(perOrderLimit).append("\");\n");
            drl.append("    }\n");
        }

        drl.append("    if (_pts.compareTo(java.math.BigDecimal.ZERO) > 0) {\n");
        drl.append("        collector.awardPoints($event.getProgramCode(), $event.getMemberId(), \"REWARD\", _pts, \"").append(ruleCode).append("\", null);\n");
        drl.append("    }\n");

        drl.append("end\n");
        return drl.toString();
    }
}