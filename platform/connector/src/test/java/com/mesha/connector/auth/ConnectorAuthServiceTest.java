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
    void login_savesCredentialsFromValidationResponse() {
        when(connectorAuthClient.validate("mcat_supplied"))
            .thenReturn(new ConnectorTokenValidationResponse(3600));

        service.login("mcat_supplied");

        verify(tokenStore).save(argThat(c -> c.accessToken().equals("mcat_supplied")));
    }

    @Test
    void login_invalidToken_propagatesException() {
        when(connectorAuthClient.validate("mcat_bad")).thenThrow(new ConnectorAuthException("rejected"));

        assertThatThrownBy(() -> service.login("mcat_bad")).isInstanceOf(ConnectorAuthException.class);
        verify(tokenStore, never()).save(any());
    }

    @Test
    void getValidAccessToken_notAuthenticated_throws() {
        when(tokenStore.load()).thenReturn(Optional.empty());

        assertThatThrownBy(service::getValidAccessToken).isInstanceOf(ConnectorAuthException.class);
    }

    @Test
    void getValidAccessToken_freshToken_returnsToken() {
        ConnectorCredentials fresh = new ConnectorCredentials("mcat_fresh", Instant.now().plusSeconds(3600));
        when(tokenStore.load()).thenReturn(Optional.of(fresh));

        String token = service.getValidAccessToken();

        assertThat(token).isEqualTo("mcat_fresh");
    }

    @Test
    void getValidAccessToken_expiredToken_clearsStoreAndThrowsWithReLoginMessage() {
        ConnectorCredentials expired = new ConnectorCredentials("mcat_old", Instant.now().minusSeconds(5));
        when(tokenStore.load()).thenReturn(Optional.of(expired));

        assertThatThrownBy(service::getValidAccessToken)
            .isInstanceOf(ConnectorAuthException.class)
            .hasMessageContaining("login");
        verify(tokenStore).clear();
    }
}
