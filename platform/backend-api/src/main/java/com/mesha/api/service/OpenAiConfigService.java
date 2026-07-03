package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.ai.OpenAiCredential;
import com.mesha.api.config.OpenAiProperties;
import com.mesha.api.dto.OpenAiConfigDto;
import com.mesha.api.dto.SaveOpenAiConfigRequest;
import com.mesha.api.model.OpenAiAuthMode;
import com.mesha.api.model.User;
import com.mesha.api.model.UserOpenAiConfig;
import com.mesha.api.repository.UserOpenAiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages a user's OpenAI credential (API key or ChatGPT-subscription token) and
 * resolves it — refreshing an expired ChatGPT access token when possible — for
 * AI ticket-draft generation.
 */
@Service
public class OpenAiConfigService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConfigService.class);
    // Refresh a ChatGPT access token slightly before it actually expires.
    private static final long REFRESH_SKEW_SECONDS = 120;

    private final UserOpenAiConfigRepository configRepository;
    private final SecretCipher secretCipher;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final TransactionTemplate transactionTemplate;

    public OpenAiConfigService(UserOpenAiConfigRepository configRepository,
                               SecretCipher secretCipher,
                               OpenAiProperties properties,
                               ObjectMapper objectMapper,
                               RestClient.Builder restClientBuilder,
                               PlatformTransactionManager transactionManager) {
        this.configRepository = configRepository;
        this.secretCipher = secretCipher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Optional<OpenAiConfigDto> getConfig(UUID userId) {
        return configRepository.findByUserId(userId).map(OpenAiConfigDto::from);
    }

    public boolean isConnected(UUID userId) {
        return configRepository.existsByUserId(userId);
    }

    @Transactional
    public OpenAiConfigDto saveConfig(User user, SaveOpenAiConfigRequest req) {
        if (req.authMode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authMode is required");
        }

        UserOpenAiConfig config = configRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserOpenAiConfig c = new UserOpenAiConfig();
                    c.setUser(user);
                    return c;
                });

        config.setAuthMode(req.authMode());
        config.setModel(blankToNull(req.model()));
        config.setStatus("connected");

        // On update, a blank secret field means "keep the existing one" — so the user
        // can change non-secret fields (model, accountId) without re-entering credentials.
        // Switching modes still clears the other mode's fields.
        boolean modeChanged = config.getId() != null && config.getAuthMode() != req.authMode();
        if (req.authMode() == OpenAiAuthMode.API_KEY) {
            if (!isBlank(req.apiKey())) {
                config.setApiKeyEnc(secretCipher.encrypt(req.apiKey().trim()));
            } else if (modeChanged || config.getApiKeyEnc() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey is required for API_KEY mode");
            }
            // Clear any ChatGPT-token fields from a previous mode.
            config.setAccessTokenEnc(null);
            config.setRefreshTokenEnc(null);
            config.setAccountId(null);
            config.setAccessTokenExpiresAt(null);
        } else { // CHATGPT_TOKEN
            if (!isBlank(req.accessToken())) {
                config.setAccessTokenEnc(secretCipher.encrypt(req.accessToken().trim()));
                // We can't read the JWT expiry reliably here; assume expired so the first
                // use refreshes if a refresh token is present, otherwise uses it as-is.
                config.setAccessTokenExpiresAt(Instant.now());
            } else if (modeChanged || config.getAccessTokenEnc() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "accessToken is required for CHATGPT_TOKEN mode");
            }
            if (!isBlank(req.refreshToken())) {
                config.setRefreshTokenEnc(secretCipher.encrypt(req.refreshToken().trim()));
            }
            if (!isBlank(req.accountId())) {
                config.setAccountId(req.accountId().trim());
            }
            config.setApiKeyEnc(null);
        }

        config = configRepository.save(config);
        log.info("OpenAI config saved userId={} authMode={}", user.getId(), config.getAuthMode());
        return OpenAiConfigDto.from(config);
    }

    @Transactional
    public void disconnect(UUID userId) {
        if (!configRepository.existsByUserId(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No OpenAI config for user");
        }
        configRepository.deleteByUserId(userId);
        log.info("OpenAI config deleted userId={}", userId);
    }

    /**
     * Decrypt and return a usable credential, refreshing an expired ChatGPT access
     * token when a refresh token is available. Throws 412 if not connected.
     *
     * <p>Not {@code @Transactional}: the OAuth refresh makes an external network call,
     * which must not hold a DB connection open. Reads/writes use short
     * {@link TransactionTemplate} blocks around that call instead.
     */
    public OpenAiCredential resolveCredential(UUID userId) {
        UserOpenAiConfig config = transactionTemplate
                .execute(status -> configRepository.findByUserId(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "OpenAI is not connected for this user. Connect your account in settings."));

        if (config.getAuthMode() == OpenAiAuthMode.API_KEY) {
            return new OpenAiCredential(OpenAiAuthMode.API_KEY,
                    secretCipher.decrypt(config.getApiKeyEnc()), null, null, config.getModel());
        }

        String accessToken = maybeRefresh(userId, config);
        return new OpenAiCredential(OpenAiAuthMode.CHATGPT_TOKEN,
                null, accessToken, config.getAccountId(), config.getModel());
    }

    private String maybeRefresh(UUID userId, UserOpenAiConfig config) {
        boolean expired = config.getAccessTokenExpiresAt() == null
                || config.getAccessTokenExpiresAt().isBefore(Instant.now().plusSeconds(REFRESH_SKEW_SECONDS));

        if (!expired || config.getRefreshTokenEnc() == null) {
            // Either still valid, or we have no way to refresh — use what we have.
            return secretCipher.decrypt(config.getAccessTokenEnc());
        }

        String refreshToken = secretCipher.decrypt(config.getRefreshTokenEnc());
        try {
            Map<String, Object> body = Map.of(
                    "client_id", properties.getOauthClientId(),
                    "grant_type", "refresh_token",
                    "refresh_token", refreshToken,
                    "scope", "openid profile email"
            );
            // External call happens OUTSIDE any transaction.
            String response = restClient.post()
                    .uri(properties.getOauthTokenUrl())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(response);
            String newAccess = json.path("access_token").asText(null);
            if (newAccess == null || newAccess.isBlank()) {
                throw new IllegalStateException("no access_token in refresh response");
            }
            String newRefresh = json.path("refresh_token").asText(null);
            long expiresIn = json.path("expires_in").asLong(0);

            transactionTemplate.executeWithoutResult(status ->
                configRepository.findByUserId(userId).ifPresent(current -> {
                    current.setAccessTokenEnc(secretCipher.encrypt(newAccess));
                    if (newRefresh != null && !newRefresh.isBlank()) {
                        current.setRefreshTokenEnc(secretCipher.encrypt(newRefresh));
                    }
                    current.setAccessTokenExpiresAt(expiresIn > 0
                            ? Instant.now().plusSeconds(expiresIn)
                            : Instant.now().plus(1, ChronoUnit.HOURS));
                    current.setStatus("connected");
                    configRepository.save(current);
                }));
            log.info("OpenAI ChatGPT token refreshed userId={}", userId);
            return newAccess;
        } catch (Exception e) {
            log.error("OpenAI ChatGPT token refresh failed userId={} error={}", userId, e.getMessage());
            transactionTemplate.executeWithoutResult(status ->
                configRepository.findByUserId(userId).ifPresent(current -> {
                    current.setStatus("expired");
                    configRepository.save(current);
                }));
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Your ChatGPT token has expired and could not be refreshed. "
                    + "Re-paste it from ~/.codex/auth.json in settings.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
