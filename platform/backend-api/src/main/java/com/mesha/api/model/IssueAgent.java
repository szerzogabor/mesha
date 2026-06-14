package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_agents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "agent_definition_id"}),
       indexes = {
           @Index(name = "idx_issue_agents_issue_id", columnList = "issue_id"),
           @Index(name = "idx_issue_agents_agent_definition_id", columnList = "agent_definition_id")
       })
public class IssueAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_definition_id", nullable = false)
    private AgentDefinition agentDefinition;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    public UUID getId() { return id; }
    public Issue getIssue() { return issue; }
    public void setIssue(Issue issue) { this.issue = issue; }
    public AgentDefinition getAgentDefinition() { return agentDefinition; }
    public void setAgentDefinition(AgentDefinition agentDefinition) { this.agentDefinition = agentDefinition; }
    public Instant getAssignedAt() { return assignedAt; }
    public User getAssignedBy() { return assignedBy; }
    public void setAssignedBy(User assignedBy) { this.assignedBy = assignedBy; }
}
