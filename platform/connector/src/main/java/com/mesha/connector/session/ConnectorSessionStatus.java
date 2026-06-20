package com.mesha.connector.session;

/** Mirrors the subset of {@code com.mesha.api.model.ConnectorAgentSessionStatus} a connector can transition into. */
public enum ConnectorSessionStatus {
    PREPARING,
    RUNNING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}
