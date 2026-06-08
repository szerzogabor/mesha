package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class CommentWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "issue_id", columnDefinition = "uuid", nullable = false)
    private UUID issueId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserWorkerRecord author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getIssueId() { return issueId; }
    public String getBody() { return body; }
    public UserWorkerRecord getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
}
