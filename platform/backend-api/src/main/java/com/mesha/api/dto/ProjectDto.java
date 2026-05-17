package com.mesha.api.dto;

import com.mesha.api.model.Project;
import java.time.Instant;
import java.util.UUID;

public record ProjectDto(UUID id, UUID workspaceId, String name, String description, Instant createdAt, Instant updatedAt) {
    public static ProjectDto from(Project p) {
        return new ProjectDto(
            p.getId(),
            p.getWorkspace().getId(),
            p.getName(),
            p.getDescription(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
