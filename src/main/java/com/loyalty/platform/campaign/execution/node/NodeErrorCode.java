package com.loyalty.platform.campaign.execution.node;

public enum NodeErrorCode {

    NODE_TYPE_NOT_FOUND("N001", "Node type not found"),
    NODE_HANDLER_NOT_FOUND("N002", "Node handler not found"),
    NODE_CONFIG_INVALID("N003", "Node configuration is invalid"),
    NODE_CONFIG_MISSING_REQUIRED("N004", "Required config field missing"),
    NODE_EXECUTION_FAILED("N005", "Node execution failed"),
    NODE_TIMEOUT("N006", "Node execution timeout"),
    NODE_RETRY_EXHAUSTED("N007", "Node retry exhausted");

    private final String code;
    private final String message;

    NodeErrorCode(String code, String message) { this.code = code; this.message = message; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
