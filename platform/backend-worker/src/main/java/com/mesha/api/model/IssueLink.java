package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_links",
       indexes = {
           @Index(name = "idx_issue_links_source_id", columnList = "source_issue_id"),
           @Index(name = "idx_issue_links_target_id", columnList = "target_issue_id")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_issue_link", columnNames = {"source_issue_id", "target_issue_id", "link_type"})
       })
public class IssueLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_issue_id", nullable = false)
    private Issue sourceIssue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_issue_id", nullable = false)
    private Issue targetIssue;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private IssueLinkType linkType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public Issue getSourceIssue() { return sourceIssue; }
    public void setSourceIssue(Issue sourceIssue) { this.sourceIssue = sourceIssue; }
    public Issue getTargetIssue() { return targetIssue; }
    public void setTargetIssue(Issue targetIssue) { this.targetIssue = targetIssue; }
    public IssueLinkType getLinkType() { return linkType; }
    public void setLinkType(IssueLinkType linkType) { this.linkType = linkType; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
