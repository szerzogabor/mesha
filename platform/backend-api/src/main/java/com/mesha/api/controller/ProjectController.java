package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<ProjectDto>> list(@PathVariable String workspaceId) {
        List<ProjectDto> projects = projectService.listByWorkspace(UUID.fromString(workspaceId))
            .stream().map(ProjectDto::from).toList();
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ProjectDto> create(@PathVariable String workspaceId,
                                             @CurrentUser User user,
                                             @Valid @RequestBody CreateProjectRequest req) {
        ProjectDto project = ProjectDto.from(projectService.create(UUID.fromString(workspaceId), req));
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping("/{projectId}")
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ProjectDto> get(@PathVariable String workspaceId,
                                          @PathVariable UUID projectId) {
        return ResponseEntity.ok(ProjectDto.from(projectService.getById(projectId)));
    }

    @PatchMapping("/{projectId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ProjectDto> update(@PathVariable String workspaceId,
                                             @PathVariable UUID projectId,
                                             @Valid @RequestBody UpdateProjectRequest req) {
        return ResponseEntity.ok(ProjectDto.from(projectService.update(projectId, req)));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> delete(@PathVariable String workspaceId,
                                       @PathVariable UUID projectId) {
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }
}
