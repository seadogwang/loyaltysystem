package com.loyalty.platform.campaign.execution.runtime;

public enum ExecutionState {
    CREATED, DEPLOYING, DEPLOYED, STARTING, RUNNING,
    PAUSED, COMPLETED, PARTIAL_FAILED, FAILED, CANCELLED;

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == PARTIAL_FAILED;
    }
    public boolean isTerminal() { return this == COMPLETED || this == CANCELLED; }
}
