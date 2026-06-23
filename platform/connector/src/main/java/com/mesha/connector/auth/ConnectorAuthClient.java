package com.mesha.connector.auth;

import com.mesha.connector.config.ConnectorProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Talks to the backend API's /api/connector/auth/* endpoints. Kept separate from
 * {@link ConnectorAuthService} so the HTTP concerns don't leak into the credential lifecycle logic.
 */
@Component
class ConnectorAuthClient {

    private final RestClient restClient;

    ConnectorAuthClient(ConnectorProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.backendUrl())
                .build();
    }

    ConnectorTokenValidationResponse validate(String accessToken) {
        try {
            return restClient.get()
                    .uri("/api/connector/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(ConnectorTokenValidationResponse.class);
        } catch (HttpClientErrorException e) {
            throw new ConnectorAuthException("Login failed: backend rejected the supplied token (" + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            throw new ConnectorAuthException("Login failed: could not reach the backend (" + e.getMessage() + ")", e);
        }
    }
}
