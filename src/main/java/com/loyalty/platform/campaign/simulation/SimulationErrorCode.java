package com.loyalty.platform.campaign.simulation;

/**
 * 模拟与优化模块业务错误码枚举。
 *
 * <p>格式：S + 三位数字序号。
 */
public enum SimulationErrorCode {

    NO_MEMBERS_FOUND("S001", "No members found for the specified segment"),
    NO_HISTORICAL_DATA("S002", "Insufficient historical data for baseline calculation"),
    SIMULATION_FAILED("S003", "Simulation execution failed"),
    OPTIMIZATION_FAILED("S004", "Optimization execution failed"),
    INVALID_CONSTRAINTS("S005", "Invalid optimization constraints"),
    OPTIMIZATION_NOT_CONVERGED("S006", "Optimization did not converge"),
    SCENARIO_NOT_FOUND("S007", "Scenario not found");

    private final String code;
    private final String message;

    SimulationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
