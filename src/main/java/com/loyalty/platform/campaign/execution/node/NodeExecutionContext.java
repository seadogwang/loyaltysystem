package com.loyalty.platform.campaign.execution.node;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NodeExecutionContext {
    private String planId;
    private String nodeId;
    private String nodeType;
    private JsonNode config;
    private Map<String, Object> inputs;
    private Map<String, Object> sharedState;
    private String executionId;
    private long processInstanceKey;
}
