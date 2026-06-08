package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "github_repositories")
public class GitHubRepositoryWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String owner;

    @Column(name = "html_url", nullable = false)
    private String htmlUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Column(nullable = false)
    private Boolean connected;

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getOwner() { return owner; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public Boolean getConnected() { return connected; }
}
