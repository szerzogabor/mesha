package com.mesha.connector.auth;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Attaches a valid connector access token to every outgoing request to the backend API,
 * refreshing it first if it's near expiry.
 */
@Component
public class ConnectorAuthInterceptor implements ClientHttpRequestInterceptor {

    private final ConnectorAuthService connectorAuthService;

    public ConnectorAuthInterceptor(ConnectorAuthService connectorAuthService) {
        this.connectorAuthService = connectorAuthService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(connectorAuthService.getValidAccessToken());
        return execution.execute(request, body);
    }
}
