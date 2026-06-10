package com.mesha.api.dto;

import com.mesha.api.model.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectStatusDto(
    UUID id,
    UUID projectId,
    String name,
    String color,
    Integer position,
    Instant createdAt
) {
    public static ProjectStatusDto from(ProjectStatus s) {
        return new ProjectStatusDto(
            s.getId(),
            s.getProject().getId(),
            s.getName(),
            s.getColor(),
            s.getPosition(),
            s.getCreatedAt()
        );
    }
}
