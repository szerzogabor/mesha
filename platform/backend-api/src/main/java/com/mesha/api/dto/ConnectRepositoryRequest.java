package com.mesha.api.dto;

import jakarta.validation.constraints.NotNull;

public record ConnectRepositoryRequest(
        @NotNull Long installationId,
        @NotNull Long githubRepoId
) {}
