package com.mesha.api.dto;

import com.mesha.api.model.WorkspaceMember;
import com.mesha.api.model.WorkspaceRole;
import java.util.UUID;

public record WorkspaceMemberDto(UUID id, UUID userId, String email, String name, WorkspaceRole role) {
    public static WorkspaceMemberDto from(WorkspaceMember m) {
        return new WorkspaceMemberDto(
            m.getId(),
            m.getUser().getId(),
            m.getUser().getEmail(),
            m.getUser().getName(),
            m.getRole()
        );
    }
}
