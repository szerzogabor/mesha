package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal JPA projection of the blocks_messages table used exclusively by the
 * polling scheduler to append activity messages on state transitions.
 */
@Entity
@Table(name = "blocks_messages")
public class BlocksMessageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
}
