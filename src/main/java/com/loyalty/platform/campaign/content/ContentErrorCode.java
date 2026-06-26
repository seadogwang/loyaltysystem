package com.loyalty.platform.campaign.content;

public enum ContentErrorCode {

    ASSET_NOT_FOUND("M001", "Content asset not found"),
    ASSET_INVALID_STATUS("M002", "Asset cannot be modified in current status"),
    ASSET_ALREADY_APPROVED("M003", "Asset is already approved"),
    APPROVAL_NOT_FOUND("M004", "Approval record not found"),
    APPROVAL_ALREADY_PROCESSED("M005", "Approval already processed"),
    APPROVAL_TIMEOUT("M006", "Approval timeout"),
    VARIABLE_MISSING("M007", "Required variable missing"),
    RENDER_FAILED("M008", "Content rendering failed");

    private final String code;
    private final String message;

    ContentErrorCode(String code, String message) { this.code = code; this.message = message; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
