package com.mesha.worker.scheduling;

public enum AIExecutionState {
    CREATED,
    PLANNING,
    EXECUTING,
    WAITING_REVIEW,
    PR_OPENED,
    DONE,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELED;
    }
}
