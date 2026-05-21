package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_drafts",
       indexes = {
           @Index(name = "idx_ai_drafts_project_id", columnList = "project_id"),
           @Index(name = "idx_ai_drafts_created_by", columnList = "created_by")
       })
public class AIDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AIDraftStatus status = AIDraftStatus.PENDING;

    @Column(name = "generated_title", columnDefinition = "TEXT")
    private String generatedTitle;

    @Column(name = "generated_description", columnDefinition = "TEXT")
    private String generatedDescription;

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(name = "suggested_labels", columnDefinition = "TEXT")
    private String suggestedLabels;

    @Column(name = "priority_suggestion", length = 20)
    private String prioritySuggestion;

    @Column(name = "implementation_notes", columnDefinition = "TEXT")
    private String implementationNotes;

    @Column(name = "scope_notes", columnDefinition = "TEXT")
    private String scopeNotes;

    @Column(name = "out_of_scope_notes", columnDefinition = "TEXT")
    private String outOfScopeNotes;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public AIDraftStatus getStatus() { return status; }
    public void setStatus(AIDraftStatus status) { this.status = status; }
    public String getGeneratedTitle() { return generatedTitle; }
    public void setGeneratedTitle(String generatedTitle) { this.generatedTitle = generatedTitle; }
    public String getGeneratedDescription() { return generatedDescription; }
    public void setGeneratedDescription(String generatedDescription) { this.generatedDescription = generatedDescription; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getSuggestedLabels() { return suggestedLabels; }
    public void setSuggestedLabels(String suggestedLabels) { this.suggestedLabels = suggestedLabels; }
    public String getPrioritySuggestion() { return prioritySuggestion; }
    public void setPrioritySuggestion(String prioritySuggestion) { this.prioritySuggestion = prioritySuggestion; }
    public String getImplementationNotes() { return implementationNotes; }
    public void setImplementationNotes(String implementationNotes) { this.implementationNotes = implementationNotes; }
    public String getScopeNotes() { return scopeNotes; }
    public void setScopeNotes(String scopeNotes) { this.scopeNotes = scopeNotes; }
    public String getOutOfScopeNotes() { return outOfScopeNotes; }
    public void setOutOfScopeNotes(String outOfScopeNotes) { this.outOfScopeNotes = outOfScopeNotes; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
