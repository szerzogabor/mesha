package com.mesha.connector.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConnectorAuthServiceTest {

    @Mock private ConnectorAuthClient connectorAuthClient;
    @Mock private ConnectorTokenStore tokenStore;

    private ConnectorAuthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConnectorAuthService(connectorAuthClient, tokenStore);
    }

    @Test
    void login_savesCredentialsFromBackendResponse() {
        when(connectorAuthClient.login("supplied-token"))
            .thenReturn(new ConnectorTokenResponse("mcat_new", "mcrt_new", 3600, "Bearer"));

        service.login("supplied-token");

        verify(tokenStore).save(argThat(c ->
            c.accessToken().equals("mcat_new") && c.refreshToken().equals("mcrt_new")));
    }

    @Test
    void login_invalidToken_propagatesException() {
        when(connectorAuthClient.login("bad-token")).thenThrow(new ConnectorAuthException("rejected"));

        assertThatThrownBy(() -> service.login("bad-token")).isInstanceOf(ConnectorAuthException.class);
        verify(tokenStore, never()).save(any());
    }

    @Test
    void getValidAccessToken_notAuthenticated_throws() {
        when(tokenStore.load()).thenReturn(Optional.empty());

        assertThatThrownBy(service::getValidAccessToken).isInstanceOf(ConnectorAuthException.class);
    }

    @Test
    void getValidAccessToken_freshToken_returnsWithoutRefreshing() {
        ConnectorCredentials fresh = new ConnectorCredentials("mcat_fresh", Instant.now().plusSeconds(3600), "mcrt_x");
        when(tokenStore.load()).thenReturn(Optional.of(fresh));

        String token = service.getValidAccessToken();

        assertThat(token).isEqualTo("mcat_fresh");
        verify(connectorAuthClient, never()).refresh(any());
    }

    @Test
    void getValidAccessToken_nearExpiry_refreshesAndSavesNewCredentials() {
        ConnectorCredentials expiring = new ConnectorCredentials("mcat_old", Instant.now().plusSeconds(5), "mcrt_old");
        when(tokenStore.load()).thenReturn(Optional.of(expiring));
        when(connectorAuthClient.refresh("mcrt_old"))
            .thenReturn(new ConnectorTokenResponse("mcat_refreshed", "mcrt_refreshed", 3600, "Bearer"));

        String token = service.getValidAccessToken();

        assertThat(token).isEqualTo("mcat_refreshed");
        verify(tokenStore).save(argThat(c -> c.accessToken().equals("mcat_refreshed")));
    }

    @Test
    void getValidAccessToken_refreshRejected_clearsStoreAndThrows() {
        ConnectorCredentials expiring = new ConnectorCredentials("mcat_old", Instant.now().plusSeconds(5), "mcrt_old");
        when(tokenStore.load()).thenReturn(Optional.of(expiring));
        when(connectorAuthClient.refresh("mcrt_old")).thenThrow(new ConnectorAuthException("expired"));

        assertThatThrownBy(service::getValidAccessToken).isInstanceOf(ConnectorAuthException.class);
        verify(tokenStore).clear();
    }
}
