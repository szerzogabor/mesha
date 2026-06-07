package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Read-only JPA projection of workspace_blocks_config used by the worker to
 * retrieve the per-workspace Blocks API key without a dependency on backend-api.
 */
@Entity
@Table(name = "workspace_blocks_config")
public class WorkspaceBlocksConfigWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "api_key_enc", nullable = false, columnDefinition = "TEXT")
    private String apiKeyEnc;

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getApiKeyEnc() { return apiKeyEnc; }
}
