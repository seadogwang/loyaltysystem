package com.loyalty.platform.campaign.canvas;

/**
 * 编译器业务错误码枚举。
 */
public enum CompilerErrorCode {

    GRAPH_EMPTY("C001", "Canvas graph is empty"),
    CYCLE_DETECTED("C002", "DAG contains cycle"),
    MISSING_START("C003", "No START node found"),
    MISSING_END("C004", "No END node found"),
    INVALID_NODE_TYPE("C005", "Invalid node type"),
    ISOLATED_NODE("C006", "Isolated node detected"),
    APPROVAL_NO_CONFIG("C007", "Approval node missing configuration"),
    CONDITION_NO_BRANCH("C008", "Condition node has no outgoing branches"),
    WORKER_NOT_REGISTERED("C009", "Worker not registered for node type"),
    AI_GENERATION_FAILED("C010", "AI DAG generation failed");

    private final String code;
    private final String message;

    CompilerErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
