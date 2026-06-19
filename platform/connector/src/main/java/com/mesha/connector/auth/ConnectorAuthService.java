package com.mesha.connector.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Owns the connector's authentication lifecycle: exchanging a Mesha-issued token for
 * connector credentials, persisting them, and refreshing the access token before it expires.
 */
@Service
public class ConnectorAuthService {

    private static final long REFRESH_SKEW_SECONDS = 30;

    private final ConnectorAuthClient connectorAuthClient;
    private final ConnectorTokenStore tokenStore;

    public ConnectorAuthService(ConnectorAuthClient connectorAuthClient, ConnectorTokenStore tokenStore) {
        this.connectorAuthClient = connectorAuthClient;
        this.tokenStore = tokenStore;
    }

    public void login(String bearerToken) {
        ConnectorTokenResponse response = connectorAuthClient.login(bearerToken);
        tokenStore.save(toCredentials(response));
    }

    public boolean isAuthenticated() {
        return tokenStore.load().isPresent();
    }

    /**
     * Returns an access token guaranteed to be valid for at least {@link #REFRESH_SKEW_SECONDS},
     * transparently refreshing it first if needed.
     */
    public synchronized String getValidAccessToken() {
        ConnectorCredentials credentials = tokenStore.load()
                .orElseThrow(() -> new ConnectorAuthException("Not authenticated. Run the `login` command first."));

        if (credentials.accessTokenExpiresAt().isBefore(Instant.now().plusSeconds(REFRESH_SKEW_SECONDS))) {
            credentials = refresh(credentials);
        }
        return credentials.accessToken();
    }

    private ConnectorCredentials refresh(ConnectorCredentials current) {
        try {
            ConnectorCredentials updated = toCredentials(connectorAuthClient.refresh(current.refreshToken()));
            tokenStore.save(updated);
            return updated;
        } catch (ConnectorAuthException e) {
            tokenStore.clear();
            throw new ConnectorAuthException("Session expired. Run the `login` command again.", e);
        }
    }

    private ConnectorCredentials toCredentials(ConnectorTokenResponse response) {
        return new ConnectorCredentials(
                response.accessToken(),
                Instant.now().plusSeconds(response.expiresIn()),
                response.refreshToken()
        );
    }
}
