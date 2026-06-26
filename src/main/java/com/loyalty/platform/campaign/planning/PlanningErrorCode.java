package com.loyalty.platform.campaign.planning;

/**
 * 规划模块业务错误码枚举。
 *
 * <p>格式：P + 三位数字序号。
 * 用于 Planning Workspace / Goal / Initiative / Portfolio 相关业务异常。
 */
public enum PlanningErrorCode {

    WORKSPACE_NOT_FOUND("P001", "Workspace not found"),
    WORKSPACE_LOCKED("P002", "Workspace is locked by another user"),
    GOAL_NOT_FOUND("P003", "Goal not found"),
    GOAL_ALREADY_ACTIVE("P004", "Another goal is already active in this workspace"),
    GOAL_CANNOT_ACTIVATE("P005", "Only DRAFT or PAUSED goal can be activated"),
    INITIATIVE_NOT_FOUND("P006", "Initiative not found"),
    INITIATIVE_GOAL_NOT_ACTIVE("P007", "Cannot activate initiative: Goal is not ACTIVE"),
    PORTFOLIO_NOT_FOUND("P008", "Portfolio not found"),
    PORTFOLIO_ALREADY_LOCKED("P009", "Portfolio is already locked"),
    NO_INITIATIVES_FOR_OPTIMIZATION("P010", "No active initiatives found for optimization"),
    PERMISSION_DENIED("P011", "No permission to access this resource");

    private final String code;
    private final String message;

    PlanningErrorCode(String code, String message) {
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
