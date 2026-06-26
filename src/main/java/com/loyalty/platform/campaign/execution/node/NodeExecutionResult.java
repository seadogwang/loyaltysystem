package com.loyalty.platform.campaign.execution.node;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NodeExecutionResult {
    private String nodeId;
    private String status;
    private Map<String, Object> outputs;
    private String errorMessage;
    private long durationMs;
}
