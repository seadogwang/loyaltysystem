package com.loyalty.platform.campaign.opportunity;

/**
 * 机会智能业务异常枚举。
 */
public enum OpportunityErrorCode {
    GOAL_NOT_ACTIVE("O001", "Goal must be ACTIVE to discover opportunities"),
    NO_ELIGIBLE_MEMBERS("O002", "No eligible members found for the given criteria"),
    ML_SERVICE_UNAVAILABLE("O003", "ML prediction service is temporarily unavailable"),
    OPPORTUNITY_NOT_FOUND("O004", "Opportunity not found"),
    OPPORTUNITY_ALREADY_CONSUMED("O005", "Opportunity has already been consumed"),
    OPPORTUNITY_EXPIRED("O006", "Opportunity has expired"),
    SKILL_EXECUTION_FAILED("O007", "External skill execution failed");

    private final String code;
    private final String message;

    OpportunityErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() { return code; }
    public String message() { return message; }
}
