package com.mesha.api.dto;

import com.mesha.api.model.User;
import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String email, String name, Instant createdAt) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getName(), u.getCreatedAt());
    }
}
