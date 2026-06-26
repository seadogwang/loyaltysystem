package com.loyalty.platform.campaign.execution.runtime;

public enum ExecutionRuntimeErrorCode {

    PLAN_NOT_FOUND("R001", "Campaign plan not found"),
    PLAN_ALREADY_RUNNING("R002", "Plan is already running"),
    PLAN_ALREADY_COMPLETED("R003", "Plan already completed"),
    COMPILATION_FAILED("R004", "BPMN compilation failed"),
    DEPLOY_FAILED("R005", "Zeebe deployment failed"),
    START_FAILED("R006", "Zeebe start failed"),
    EXECUTION_NOT_FOUND("R007", "Execution not found"),
    EXECUTION_ALREADY_COMPLETED("R008", "Execution already completed"),
    CANCEL_FAILED("R009", "Cancel execution failed"),
    NODE_HANDLER_NOT_FOUND("R010", "Node handler not found"),
    NODE_EXECUTION_FAILED("R011", "Node execution failed"),
    STEP_UPDATE_FAILED("R012", "Step status update failed");

    private final String code;
    private final String message;

    ExecutionRuntimeErrorCode(String code, String message) { this.code = code; this.message = message; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
