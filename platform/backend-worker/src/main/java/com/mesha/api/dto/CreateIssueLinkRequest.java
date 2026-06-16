package com.mesha.api.dto;

import com.mesha.api.model.IssueLinkType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateIssueLinkRequest(
    @NotNull UUID targetIssueId,
    @NotNull IssueLinkType linkType
) {}
