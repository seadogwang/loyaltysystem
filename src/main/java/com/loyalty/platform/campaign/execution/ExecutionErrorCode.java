package com.loyalty.platform.campaign.execution;

/**
 * 执行引擎业务错误码枚举。
 *
 * <p>格式：E + 三位数字序号。
 */
public enum ExecutionErrorCode {

    PLAN_NOT_FOUND("E001", "Campaign plan not found"),
    PLAN_NOT_DEPLOYED("E002", "Plan not deployed, please deploy first"),
    PLAN_ALREADY_RUNNING("E003", "Plan is already running"),
    PLAN_ALREADY_COMPLETED("E004", "Plan already completed"),
    ZEEBE_DEPLOY_FAILED("E005", "Zeebe deployment failed"),
    ZEEBE_START_FAILED("E006", "Zeebe start failed"),
    WORKER_EXECUTION_FAILED("E007", "Worker execution failed"),
    IDEMPOTENCY_VIOLATION("E008", "Duplicate execution detected");

    private final String code;
    private final String message;

    ExecutionErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
