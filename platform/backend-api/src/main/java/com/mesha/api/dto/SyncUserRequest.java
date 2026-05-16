package com.mesha.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SyncUserRequest(
    @NotBlank @Email String email,
    String name
) {}
