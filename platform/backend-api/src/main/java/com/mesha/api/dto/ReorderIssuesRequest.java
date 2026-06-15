package com.mesha.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderIssuesRequest(
    @NotNull String status,
    @NotEmpty List<UUID> issueIds
) {}
