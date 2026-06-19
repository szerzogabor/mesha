package com.mesha.api.service;

import com.mesha.api.dto.ConnectorTokenResponse;
import com.mesha.api.model.ConnectorCredential;
import com.mesha.api.model.User;
import com.mesha.api.repository.ConnectorCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConnectorAuthServiceTest {

    @Mock private ConnectorCredentialRepository connectorCredentialRepository;

    private ConnectorAuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConnectorAuthService(connectorCredentialRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void login_issuesTokenPairAndPersistsHashedCredential() {
        when(connectorCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConnectorTokenResponse response = service.login(user);

        assertThat(response.accessToken()).startsWith("mcat_");
        assertThat(response.refreshToken()).startsWith("mcrt_");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(ConnectorAuthService.ACCESS_TOKEN_TTL.toSeconds());

        ArgumentCaptor<ConnectorCredential> captor = ArgumentCaptor.forClass(ConnectorCredential.class);
        verify(connectorCredentialRepository).save(captor.capture());
        ConnectorCredential saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getAccessTokenHash()).isNotEqualTo(response.accessToken());
        assertThat(saved.getRefreshTokenHash()).isNotEqualTo(response.refreshToken());
    }

    @Test
    void validateAccessToken_validToken_returnsUserId() {
        when(connectorCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ConnectorCredential persisted = new ConnectorCredential();
        ConnectorTokenResponse issued = captureCredentialOnSave(persisted);

        when(connectorCredentialRepository.findByAccessTokenHash(persisted.getAccessTokenHash()))
            .thenReturn(Optional.of(persisted));

        Optional<UUID> result = service.validateAccessToken(issued.accessToken());

        assertThat(result).contains(user.getId());
    }

    @Test
    void validateAccessToken_unknownToken_returnsEmpty() {
        when(connectorCredentialRepository.findByAccessTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.validateAccessToken("mcat_does-not-exist")).isEmpty();
    }

    @Test
    void validateAccessToken_expiredToken_returnsEmpty() {
        when(connectorCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ConnectorCredential persisted = new ConnectorCredential();
        ConnectorTokenResponse issued = captureCredentialOnSave(persisted);
        persisted.setAccessTokenExpiresAt(Instant.now().minusSeconds(1));

        when(connectorCredentialRepository.findByAccessTokenHash(persisted.getAccessTokenHash()))
            .thenReturn(Optional.of(persisted));

        assertThat(service.validateAccessToken(issued.accessToken())).isEmpty();
    }

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() {
        when(connectorCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ConnectorCredential persisted = new ConnectorCredential();
        ConnectorTokenResponse issued = captureCredentialOnSave(persisted);

        when(connectorCredentialRepository.findByRefreshTokenHash(persisted.getRefreshTokenHash()))
            .thenReturn(Optional.of(persisted));

        ConnectorTokenResponse refreshed = service.refresh(issued.refreshToken());

        assertThat(refreshed.accessToken()).isNotEqualTo(issued.accessToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(issued.refreshToken());
    }

    @Test
    void refresh_invalidToken_throwsUnauthorized() {
        when(connectorCredentialRepository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("mcrt_invalid"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        when(connectorCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ConnectorCredential persisted = new ConnectorCredential();
        ConnectorTokenResponse issued = captureCredentialOnSave(persisted);
        persisted.setRefreshTokenExpiresAt(Instant.now().minusSeconds(1));

        when(connectorCredentialRepository.findByRefreshTokenHash(persisted.getRefreshTokenHash()))
            .thenReturn(Optional.of(persisted));

        assertThatThrownBy(() -> service.refresh(issued.refreshToken()))
            .isInstanceOf(ResponseStatusException.class);
    }

    private ConnectorTokenResponse captureCredentialOnSave(ConnectorCredential target) {
        ConnectorTokenResponse response = service.login(user);
        ArgumentCaptor<ConnectorCredential> captor = ArgumentCaptor.forClass(ConnectorCredential.class);
        verify(connectorCredentialRepository, atLeastOnce()).save(captor.capture());
        ConnectorCredential saved = captor.getValue();
        target.setUserId(saved.getUserId());
        target.setAccessTokenHash(saved.getAccessTokenHash());
        target.setAccessTokenExpiresAt(saved.getAccessTokenExpiresAt());
        target.setRefreshTokenHash(saved.getRefreshTokenHash());
        target.setRefreshTokenExpiresAt(saved.getRefreshTokenExpiresAt());
        return response;
    }
}
