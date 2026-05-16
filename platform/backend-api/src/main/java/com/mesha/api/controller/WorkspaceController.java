package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.*;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceDto>> list(@CurrentUser User user) {
        List<WorkspaceDto> workspaces = workspaceService.listForUser(user.getId())
            .stream().map(WorkspaceDto::from).toList();
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping
    public ResponseEntity<WorkspaceDto> create(@CurrentUser User user,
                                               @Valid @RequestBody CreateWorkspaceRequest req) {
        Workspace ws = workspaceService.create(req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkspaceDto.from(ws));
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceDto> get(@PathVariable String workspaceId) {
        Workspace ws = workspaceService.getById(UUID.fromString(workspaceId));
        return ResponseEntity.ok(WorkspaceDto.from(ws));
    }

    @GetMapping("/{workspaceId}/members")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<WorkspaceMemberDto>> listMembers(@PathVariable String workspaceId) {
        List<WorkspaceMemberDto> members = workspaceService.listMembers(UUID.fromString(workspaceId))
            .stream().map(WorkspaceMemberDto::from).toList();
        return ResponseEntity.ok(members);
    }

    @PatchMapping("/{workspaceId}/members/{memberId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceMemberDto> updateMemberRole(
            @PathVariable String workspaceId,
            @PathVariable UUID memberId,
            @CurrentUser User requestingUser,
            @Valid @RequestBody UpdateMemberRoleRequest req) {
        WorkspaceMember updated = workspaceService.updateMemberRole(
            UUID.fromString(workspaceId), memberId, req.role(), requestingUser);
        return ResponseEntity.ok(WorkspaceMemberDto.from(updated));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> removeMember(@PathVariable String workspaceId,
                                             @PathVariable UUID memberId) {
        workspaceService.removeMember(UUID.fromString(workspaceId), memberId);
        return ResponseEntity.noContent().build();
    }
}
