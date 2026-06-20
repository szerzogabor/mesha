package com.mesha.connector.session;

public class SessionPollingException extends RuntimeException {

    public SessionPollingException(String message) {
        super(message);
    }

    public SessionPollingException(String message, Throwable cause) {
        super(message, cause);
    }
}
