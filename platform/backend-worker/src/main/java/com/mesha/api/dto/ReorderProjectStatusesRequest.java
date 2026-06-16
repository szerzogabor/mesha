package com.mesha.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderProjectStatusesRequest(
    @NotNull List<UUID> statusIds
) {}
