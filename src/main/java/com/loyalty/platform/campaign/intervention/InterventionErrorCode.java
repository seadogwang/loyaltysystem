package com.loyalty.platform.campaign.intervention;

public enum InterventionErrorCode {

    CAMPAIGN_NOT_RUNNING("I001", "Campaign is not in RUNNING state"),
    CAMPAIGN_ALREADY_PAUSED("I002", "Campaign is already paused"),
    CAMPAIGN_ALREADY_CANCELLED("I003", "Campaign already cancelled"),
    NODE_NOT_FOUND("I004", "Node not found in campaign graph"),
    NODE_ALREADY_COMPLETED("I005", "Node already completed"),
    NODE_ALREADY_FAILED("I006", "Node already failed"),
    INSUFFICIENT_PERMISSION("I007", "Insufficient permission for this operation"),
    INTERVENTION_NOT_FOUND("I008", "Intervention command not found"),
    INTERVENTION_ALREADY_PROCESSED("I009", "Intervention already processed"),
    EMERGENCY_STOP_REQUIRES_APPROVAL("I010", "Emergency stop requires secondary approval"),
    THROTTLE_FACTOR_INVALID("I011", "Throttle factor must be between 0 and 1");

    private final String code;
    private final String message;

    InterventionErrorCode(String code, String message) { this.code = code; this.message = message; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
