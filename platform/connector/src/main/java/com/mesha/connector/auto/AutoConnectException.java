package com.mesha.connector.auto;

public class AutoConnectException extends RuntimeException {

    public AutoConnectException(String message) {
        super(message);
    }

    public AutoConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
