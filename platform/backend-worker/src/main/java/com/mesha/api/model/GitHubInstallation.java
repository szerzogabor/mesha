package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_installations")
public class GitHubInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "account_login", nullable = false)
    private String accountLogin;

    @Column(name = "account_type", nullable = false)
    private String accountType = "User";

    @Column(name = "account_avatar_url")
    private String accountAvatarUrl;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "last_refresh_at")
    private Instant lastRefreshAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public Long getInstallationId() { return installationId; }
    public void setInstallationId(Long installationId) { this.installationId = installationId; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getAccountLogin() { return accountLogin; }
    public void setAccountLogin(String accountLogin) { this.accountLogin = accountLogin; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getAccountAvatarUrl() { return accountAvatarUrl; }
    public void setAccountAvatarUrl(String accountAvatarUrl) { this.accountAvatarUrl = accountAvatarUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastRefreshAt() { return lastRefreshAt; }
    public void setLastRefreshAt(Instant lastRefreshAt) { this.lastRefreshAt = lastRefreshAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
