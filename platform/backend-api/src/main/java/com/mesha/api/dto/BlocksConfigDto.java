package com.mesha.api.dto;

import com.mesha.api.model.WorkspaceBlocksConfig;

import java.time.Instant;
import java.util.UUID;

public record BlocksConfigDto(
    UUID id,
    UUID workspaceId,
    String status,
    Instant connectedAt,
    Instant updatedAt
) {
    public static BlocksConfigDto from(WorkspaceBlocksConfig c) {
        return new BlocksConfigDto(
            c.getId(),
            c.getWorkspace().getId(),
            c.getStatus(),
            c.getConnectedAt(),
            c.getUpdatedAt()
        );
    }
}
