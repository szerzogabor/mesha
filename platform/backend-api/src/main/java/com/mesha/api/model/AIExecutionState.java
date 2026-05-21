package com.mesha.api.model;

public enum AIExecutionState {
    CREATED,
    PLANNING,
    EXECUTING,
    WAITING_REVIEW,
    PR_OPENED,
    DONE,
    FAILED,
    CANCELED
}
