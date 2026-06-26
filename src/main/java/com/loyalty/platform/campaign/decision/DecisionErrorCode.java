package com.loyalty.platform.campaign.decision;

/**
 * 决策引擎业务错误码枚举。
 *
 * <p>格式：D + 三位数字序号。
 */
public enum DecisionErrorCode {

    PORTFOLIO_NOT_OPTIMIZED("D001", "Portfolio must be OPTIMIZED before decision"),
    NO_CANDIDATES("D002", "No viable candidates found for decision"),
    BUDGET_EXCEEDED("D003", "Total budget exceeds available limit"),
    CHANNEL_CAPACITY_EXCEEDED("D004", "Channel capacity exceeded"),
    ATTENTION_BUDGET_EXHAUSTED("D005", "User attention budget exhausted"),
    DECISION_NOT_FOUND("D006", "Decision result not found"),
    DECISION_ALREADY_APPLIED("D007", "Decision has already been applied"),
    INSUFFICIENT_BUDGET("D008", "Insufficient budget for minimum allocation");

    private final String code;
    private final String message;

    DecisionErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
