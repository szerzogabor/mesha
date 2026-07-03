package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.ai.OpenAiCredential;
import com.mesha.api.config.BlocksEncryptionProperties;
import com.mesha.api.config.OpenAiProperties;
import com.mesha.api.dto.OpenAiConfigDto;
import com.mesha.api.dto.SaveOpenAiConfigRequest;
import com.mesha.api.model.OpenAiAuthMode;
import com.mesha.api.model.User;
import com.mesha.api.model.UserOpenAiConfig;
import com.mesha.api.repository.UserOpenAiConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiConfigServiceTest {

    @Mock private UserOpenAiConfigRepository configRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private SecretCipher secretCipher;
    private OpenAiConfigService service;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        BlocksEncryptionProperties props = new BlocksEncryptionProperties();
        props.setSecret("test-secret-for-openai-config");
        secretCipher = new SecretCipher(props);
        service = new OpenAiConfigService(configRepository, secretCipher, new OpenAiProperties(),
                new ObjectMapper(), RestClient.builder(), transactionManager);

        userId = UUID.randomUUID();
        user = new User();
        ReflectionTestUtils.setField(user, "id", userId);

        when(configRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(configRepository.save(any(UserOpenAiConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saveConfig_apiKey_encryptsAndClearsTokenFields() {
        SaveOpenAiConfigRequest req = new SaveOpenAiConfigRequest(
                OpenAiAuthMode.API_KEY, "sk-test-123", null, null, null, "gpt-4o-mini");

        OpenAiConfigDto dto = service.saveConfig(user, req);

        assertThat(dto.authMode()).isEqualTo(OpenAiAuthMode.API_KEY);
        assertThat(dto.model()).isEqualTo("gpt-4o-mini");

        UserOpenAiConfig saved = captureSaved();
        assertThat(saved.getApiKeyEnc()).isNotBlank();
        assertThat(secretCipher.decrypt(saved.getApiKeyEnc())).isEqualTo("sk-test-123");
        assertThat(saved.getAccessTokenEnc()).isNull();
        assertThat(saved.getRefreshTokenEnc()).isNull();
    }

    @Test
    void saveConfig_chatgptToken_missingAccessToken_throws400() {
        SaveOpenAiConfigRequest req = new SaveOpenAiConfigRequest(
                OpenAiAuthMode.CHATGPT_TOKEN, null, "  ", null, null, null);

        assertThatThrownBy(() -> service.saveConfig(user, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("accessToken is required");
    }

    @Test
    void saveConfig_missingAuthMode_throws400() {
        SaveOpenAiConfigRequest req = new SaveOpenAiConfigRequest(null, "sk", null, null, null, null);

        assertThatThrownBy(() -> service.saveConfig(user, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("authMode is required");
    }

    @Test
    void resolveCredential_notConnected_throws412() {
        assertThatThrownBy(() -> service.resolveCredential(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void resolveCredential_apiKey_returnsDecryptedKey() {
        UserOpenAiConfig config = new UserOpenAiConfig();
        config.setUser(user);
        config.setAuthMode(OpenAiAuthMode.API_KEY);
        config.setApiKeyEnc(secretCipher.encrypt("sk-live-abc"));
        config.setModel("gpt-4o");
        when(configRepository.findByUserId(userId)).thenReturn(Optional.of(config));

        OpenAiCredential cred = service.resolveCredential(userId);

        assertThat(cred.authMode()).isEqualTo(OpenAiAuthMode.API_KEY);
        assertThat(cred.apiKey()).isEqualTo("sk-live-abc");
        assertThat(cred.model()).isEqualTo("gpt-4o");
    }

    @Test
    void resolveCredential_chatgptToken_noRefreshToken_usesStoredAccessToken() {
        UserOpenAiConfig config = new UserOpenAiConfig();
        config.setUser(user);
        config.setAuthMode(OpenAiAuthMode.CHATGPT_TOKEN);
        config.setAccessTokenEnc(secretCipher.encrypt("access-xyz"));
        config.setAccountId("acct-1");
        // Expired, but no refresh token -> should return the stored token as-is (no network).
        config.setAccessTokenExpiresAt(Instant.now().minusSeconds(3600));
        when(configRepository.findByUserId(userId)).thenReturn(Optional.of(config));

        OpenAiCredential cred = service.resolveCredential(userId);

        assertThat(cred.authMode()).isEqualTo(OpenAiAuthMode.CHATGPT_TOKEN);
        assertThat(cred.accessToken()).isEqualTo("access-xyz");
        assertThat(cred.accountId()).isEqualTo("acct-1");
    }

    private UserOpenAiConfig captureSaved() {
        org.mockito.ArgumentCaptor<UserOpenAiConfig> captor =
                org.mockito.ArgumentCaptor.forClass(UserOpenAiConfig.class);
        verify(configRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
