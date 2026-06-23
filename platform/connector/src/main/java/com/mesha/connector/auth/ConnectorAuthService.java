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

    private volatile ConnectorCredentials cachedCredentials;

    public ConnectorAuthService(ConnectorAuthClient connectorAuthClient, ConnectorTokenStore tokenStore) {
        this.connectorAuthClient = connectorAuthClient;
        this.tokenStore = tokenStore;
    }

    public void login(String accessToken) {
        ConnectorTokenValidationResponse response = connectorAuthClient.validate(accessToken);
        ConnectorCredentials credentials = new ConnectorCredentials(accessToken, Instant.now().plusSeconds(response.expiresIn()));
        tokenStore.save(credentials);
        cachedCredentials = credentials;
    }

    public boolean isAuthenticated() {
        return tokenStore.load().isPresent();
    }

    /**
     * Returns the stored access token, failing fast if it's missing or has expired
     * rather than attempting any kind of refresh — there is no refresh token to fall back on.
     * Credentials are cached in memory (re-checked from disk once expired) so frequent
     * polling doesn't hit disk on every call.
     */
    public String getValidAccessToken() {
        ConnectorCredentials credentials = cachedCredentials;
        if (credentials == null || credentials.accessTokenExpiresAt().isBefore(Instant.now())) {
            credentials = tokenStore.load()
                    .orElseThrow(() -> new ConnectorAuthException("Not authenticated. Run the `login` command first."));

            if (credentials.accessTokenExpiresAt().isBefore(Instant.now())) {
                tokenStore.clear();
                cachedCredentials = null;
                throw new ConnectorAuthException("Access token expired. Run the `login` command again with a fresh token.");
            }
            cachedCredentials = credentials;
        }
        return credentials.accessToken();
    }
}
