package com.mesha.connector.agent;

import java.util.UUID;

/** The locally persisted result of a successful {@code register} invocation. */
public record AgentRegistration(UUID agentId, String hostname, String executorType) {}
