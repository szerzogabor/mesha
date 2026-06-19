package com.mesha.api.model;

public enum ConnectorAgentSessionStatus {
    CREATED,
    QUEUED,
    CLAIMED,
    PREPARING,
    RUNNING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}
