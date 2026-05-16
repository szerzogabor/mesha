package com.mesha.api.dto;

import com.mesha.api.model.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull WorkspaceRole role) {}
