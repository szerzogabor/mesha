package com.mesha.api.security;

import com.mesha.api.model.WorkspaceRole;
import com.mesha.api.repository.WorkspaceMemberRepository;
import com.mesha.api.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Used in @PreAuthorize expressions to check workspace-level roles.
 */
@Service("workspaceSecurity")
public class WorkspaceSecurityService {

    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    public WorkspaceSecurityService(WorkspaceMemberRepository memberRepository,
                                    UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    public boolean hasAnyRole(Authentication auth, String workspaceId, WorkspaceRole... roles) {
        if (!(auth.getPrincipal() instanceof Jwt jwt)) return false;
        String clerkId = jwt.getSubject();
        return userRepository.findByClerkUserId(clerkId)
            .map(user -> memberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(
                UUID.fromString(workspaceId), user.getId(), List.of(roles)))
            .orElse(false);
    }

    public boolean isAdminOrAbove(Authentication auth, String workspaceId) {
        return hasAnyRole(auth, workspaceId,
            WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    }

    public boolean isMember(Authentication auth, String workspaceId) {
        return hasAnyRole(auth, workspaceId,
            WorkspaceRole.OWNER, WorkspaceRole.ADMIN,
            WorkspaceRole.DEVELOPER, WorkspaceRole.VIEWER);
    }
}
