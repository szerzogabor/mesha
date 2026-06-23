package com.mesha.connector.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Owns the connector's authentication lifecycle: validating the Mesha connector
 * access token issued from the web app and persisting it for reuse by future commands.
 */
@Service
public class ConnectorAuthService {

    private final ConnectorAuthClient connectorAuthClient;
    private final ConnectorTokenStore tokenStore;

    public ConnectorAuthService(ConnectorAuthClient connectorAuthClient, ConnectorTokenStore tokenStore) {
        this.connectorAuthClient = connectorAuthClient;
        this.tokenStore = tokenStore;
    }

    public void login(String accessToken) {
        ConnectorTokenValidationResponse response = connectorAuthClient.validate(accessToken);
        tokenStore.save(new ConnectorCredentials(accessToken, Instant.now().plusSeconds(response.expiresIn())));
    }

    public boolean isAuthenticated() {
        return tokenStore.load().isPresent();
    }

    /**
     * Returns the stored access token, failing fast if it's missing or has expired
     * rather than attempting any kind of refresh — there is no refresh token to fall back on.
     */
    public String getValidAccessToken() {
        ConnectorCredentials credentials = tokenStore.load()
                .orElseThrow(() -> new ConnectorAuthException("Not authenticated. Run the `login` command first."));

        if (credentials.accessTokenExpiresAt().isBefore(Instant.now())) {
            tokenStore.clear();
            throw new ConnectorAuthException("Access token expired. Run the `login` command again with a fresh token.");
        }
        return credentials.accessToken();
    }
}
