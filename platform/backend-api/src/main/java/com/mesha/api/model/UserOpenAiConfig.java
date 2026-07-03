package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-user OpenAI credential used for AI ticket-draft generation.
 *
 * <p>Two auth modes are supported (see {@link OpenAiAuthMode}): a standard
 * {@code sk-...} API key, or a ChatGPT-subscription OAuth credential extracted
 * from Codex desktop's {@code ~/.codex/auth.json}. All secret fields are stored
 * AES-encrypted.
 */
@Entity
@Table(name = "user_openai_config",
       indexes = {
           @Index(name = "idx_user_openai_config_user_id", columnList = "user_id")
       })
public class UserOpenAiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_mode", nullable = false, length = 20)
    private OpenAiAuthMode authMode;

    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

    @Column(name = "access_token_enc", columnDefinition = "TEXT")
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc", columnDefinition = "TEXT")
    private String refreshTokenEnc;

    @Column(name = "account_id", length = 128)
    private String accountId;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(length = 64)
    private String model;

    @Column(nullable = false, length = 20)
    private String status = "connected";

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public OpenAiAuthMode getAuthMode() { return authMode; }
    public void setAuthMode(OpenAiAuthMode authMode) { this.authMode = authMode; }
    public String getApiKeyEnc() { return apiKeyEnc; }
    public void setApiKeyEnc(String apiKeyEnc) { this.apiKeyEnc = apiKeyEnc; }
    public String getAccessTokenEnc() { return accessTokenEnc; }
    public void setAccessTokenEnc(String accessTokenEnc) { this.accessTokenEnc = accessTokenEnc; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; }
    public void setRefreshTokenEnc(String refreshTokenEnc) { this.refreshTokenEnc = refreshTokenEnc; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
