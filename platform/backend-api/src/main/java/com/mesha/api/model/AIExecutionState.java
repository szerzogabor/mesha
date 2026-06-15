package com.mesha.api.model;

public enum AIExecutionState {
    CREATED,
    DISPATCHING,
    PLANNING,
    EXECUTING,
    WAITING_REVIEW,
    PR_OPENED,
    DONE,
    FAILED,
    CANCELED
}
