package com.mesha.connector.auth;

public class ConnectorAuthException extends RuntimeException {

    public ConnectorAuthException(String message) {
        super(message);
    }

    public ConnectorAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
