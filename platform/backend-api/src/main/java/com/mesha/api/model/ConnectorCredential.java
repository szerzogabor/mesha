package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connector_credentials")
public class ConnectorCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_token_hash", nullable = false, unique = true)
    private String accessTokenHash;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(name = "refresh_token_expires_at", nullable = false)
    private Instant refreshTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public void setAccessTokenHash(String accessTokenHash) { this.accessTokenHash = accessTokenHash; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public void setRefreshTokenExpiresAt(Instant refreshTokenExpiresAt) { this.refreshTokenExpiresAt = refreshTokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
