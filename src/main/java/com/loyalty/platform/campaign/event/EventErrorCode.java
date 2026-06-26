package com.loyalty.platform.campaign.event;

public enum EventErrorCode {

    EVENT_PUBLISH_FAILED("EV001", "Event publish failed"),
    EVENT_PROCESS_FAILED("EV002", "Event processing failed"),
    FEEDBACK_CALCULATION_FAILED("EV003", "Feedback calculation failed"),
    DRIFT_DETECTION_FAILED("EV004", "Drift detection failed"),
    STRATEGY_ADJUSTMENT_FAILED("EV005", "Strategy adjustment failed");

    private final String code;
    private final String message;

    EventErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
