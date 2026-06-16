package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_pull_requests")
public class GitHubPullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitHubRepository repository;

    @Column(name = "github_pr_number", nullable = false)
    private Integer githubPrNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private String state = "open";

    @Column(name = "author_login")
    private String authorLogin;

    @Column(name = "author_avatar_url")
    private String authorAvatarUrl;

    @Column(name = "source_branch", nullable = false)
    private String sourceBranch;

    @Column(name = "target_branch", nullable = false)
    private String targetBranch;

    @Column(name = "html_url", nullable = false)
    private String htmlUrl;

    @Column(nullable = false)
    private Boolean draft = false;

    @Column(name = "commits_count", nullable = false)
    private Integer commitsCount = 0;

    @Column(name = "review_state")
    private String reviewState;

    @Column(name = "checks_status")
    private String checksStatus;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocks_session_id")
    private BlocksSession blocksSession;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public GitHubRepository getRepository() { return repository; }
    public void setRepository(GitHubRepository repository) { this.repository = repository; }
    public Integer getGithubPrNumber() { return githubPrNumber; }
    public void setGithubPrNumber(Integer githubPrNumber) { this.githubPrNumber = githubPrNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getAuthorLogin() { return authorLogin; }
    public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public void setAuthorAvatarUrl(String authorAvatarUrl) { this.authorAvatarUrl = authorAvatarUrl; }
    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }
    public String getTargetBranch() { return targetBranch; }
    public void setTargetBranch(String targetBranch) { this.targetBranch = targetBranch; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public Boolean getDraft() { return draft; }
    public void setDraft(Boolean draft) { this.draft = draft; }
    public Integer getCommitsCount() { return commitsCount; }
    public void setCommitsCount(Integer commitsCount) { this.commitsCount = commitsCount; }
    public String getReviewState() { return reviewState; }
    public void setReviewState(String reviewState) { this.reviewState = reviewState; }
    public String getChecksStatus() { return checksStatus; }
    public void setChecksStatus(String checksStatus) { this.checksStatus = checksStatus; }
    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public BlocksSession getBlocksSession() { return blocksSession; }
    public void setBlocksSession(BlocksSession blocksSession) { this.blocksSession = blocksSession; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
