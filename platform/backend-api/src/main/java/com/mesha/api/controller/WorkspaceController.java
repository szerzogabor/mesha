package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.*;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.WorkspaceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceDto>> list(@CurrentUser User user) {
        log.debug("Listing workspaces userId={}", user.getId());
        List<WorkspaceDto> workspaces = workspaceService.listForUser(user.getId())
            .stream().map(WorkspaceDto::from).toList();
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping
    public ResponseEntity<WorkspaceDto> create(@CurrentUser User user,
                                               @Valid @RequestBody CreateWorkspaceRequest req) {
        log.debug("Creating workspace userId={} slug={}", user.getId(), req.slug());
        Workspace ws = workspaceService.create(req, user);
        log.info("Workspace created workspaceId={} slug={} userId={}", ws.getId(), req.slug(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkspaceDto.from(ws));
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceDto> get(@PathVariable String workspaceId) {
        log.debug("Fetching workspace workspaceId={}", workspaceId);
        Workspace ws = workspaceService.getById(UUID.fromString(workspaceId));
        return ResponseEntity.ok(WorkspaceDto.from(ws));
    }

    @GetMapping("/{workspaceId}/members")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<WorkspaceMemberDto>> listMembers(@PathVariable String workspaceId) {
        log.debug("Listing workspace members workspaceId={}", workspaceId);
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
        log.info("Updating member role workspaceId={} memberId={} newRole={} requestingUserId={}",
                workspaceId, memberId, req.role(), requestingUser.getId());
        WorkspaceMember updated = workspaceService.updateMemberRole(
            UUID.fromString(workspaceId), memberId, req.role(), requestingUser);
        return ResponseEntity.ok(WorkspaceMemberDto.from(updated));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> removeMember(@PathVariable String workspaceId,
                                             @PathVariable UUID memberId) {
        log.info("Removing workspace member workspaceId={} memberId={}", workspaceId, memberId);
        workspaceService.removeMember(UUID.fromString(workspaceId), memberId);
        return ResponseEntity.noContent().build();
    }
}
