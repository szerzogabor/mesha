package com.mesha.api.dto;

import com.mesha.api.model.Workspace;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceDto(UUID id, String name, String slug, Instant createdAt) {
    public static WorkspaceDto from(Workspace w) {
        return new WorkspaceDto(w.getId(), w.getName(), w.getSlug(), w.getCreatedAt());
    }
}
