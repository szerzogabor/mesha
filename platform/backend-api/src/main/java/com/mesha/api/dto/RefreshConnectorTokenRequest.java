package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshConnectorTokenRequest(@NotBlank String refreshToken) {}
