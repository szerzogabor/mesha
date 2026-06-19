package com.mesha.connector.agent;

public class AgentRegistrationException extends RuntimeException {

    public AgentRegistrationException(String message) {
        super(message);
    }

    public AgentRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
