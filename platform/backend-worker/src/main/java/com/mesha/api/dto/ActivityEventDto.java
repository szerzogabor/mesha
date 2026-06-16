package com.mesha.api.dto;

import com.mesha.api.model.ActivityEvent;
import com.mesha.api.model.ActivityEventType;
import java.time.Instant;
import java.util.UUID;

public record ActivityEventDto(
    UUID id,
    UUID issueId,
    UserDto user,
    ActivityEventType eventType,
    String oldValue,
    String newValue,
    Instant createdAt
) {
    public static ActivityEventDto from(ActivityEvent e) {
        return new ActivityEventDto(
            e.getId(),
            e.getIssue().getId(),
            e.getUser() != null ? UserDto.from(e.getUser()) : null,
            e.getEventType(),
            e.getOldValue(),
            e.getNewValue(),
            e.getCreatedAt()
        );
    }
}
