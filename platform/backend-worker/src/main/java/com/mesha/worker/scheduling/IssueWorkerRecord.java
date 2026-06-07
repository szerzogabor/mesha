package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Minimal read-only JPA projection of the issues table for use by the worker.
 */
@Entity
@Table(name = "issues")
public class IssueWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
