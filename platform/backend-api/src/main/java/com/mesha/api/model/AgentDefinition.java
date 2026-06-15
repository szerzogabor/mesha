package com.mesha.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "name"}),
       indexes = {
           @Index(name = "idx_agent_definitions_workspace_id", columnList = "workspace_id"),
           @Index(name = "idx_agent_definitions_active", columnList = "workspace_id, active")
       })
public class AgentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 50)
    private AgentProviderType providerType;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> providerParameters = new HashMap<>();

    @Column(name = "blocks_agent_name", length = 100)
    private String blocksAgentName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AgentProviderType getProviderType() { return providerType; }
    public void setProviderType(AgentProviderType providerType) { this.providerType = providerType; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Map<String, Object> getProviderParameters() { return providerParameters; }
    public void setProviderParameters(Map<String, Object> providerParameters) { this.providerParameters = providerParameters; }
    public String getBlocksAgentName() { return blocksAgentName; }
    public void setBlocksAgentName(String blocksAgentName) { this.blocksAgentName = blocksAgentName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
