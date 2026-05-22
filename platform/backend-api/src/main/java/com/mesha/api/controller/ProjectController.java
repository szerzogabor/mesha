package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ProjectService;
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
@RequestMapping("/api/workspaces/{workspaceId}/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<ProjectDto>> list(@PathVariable String workspaceId) {
        log.debug("Listing projects workspaceId={}", workspaceId);
        List<ProjectDto> projects = projectService.listByWorkspace(UUID.fromString(workspaceId))
            .stream().map(ProjectDto::from).toList();
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ProjectDto> create(@PathVariable String workspaceId,
                                             @CurrentUser User user,
                                             @Valid @RequestBody CreateProjectRequest req) {
        log.debug("Creating project workspaceId={} userId={} name={}", workspaceId, user.getId(), req.name());
        ProjectDto project = ProjectDto.from(projectService.create(UUID.fromString(workspaceId), req));
        log.debug("Project created projectId={} workspaceId={}", project.id(), workspaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ProjectDto> get(@PathVariable String workspaceId,
                                          @PathVariable UUID projectId) {
        log.debug("Fetching project projectId={} workspaceId={}", projectId, workspaceId);
        return ResponseEntity.ok(ProjectDto.from(projectService.getById(projectId)));
    }

    @PatchMapping("/{projectId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ProjectDto> update(@PathVariable String workspaceId,
                                             @PathVariable UUID projectId,
                                             @Valid @RequestBody UpdateProjectRequest req) {
        log.debug("Updating project projectId={} workspaceId={}", projectId, workspaceId);
        return ResponseEntity.ok(ProjectDto.from(projectService.update(projectId, req)));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> delete(@PathVariable String workspaceId,
                                       @PathVariable UUID projectId) {
        log.info("Deleting project projectId={} workspaceId={}", projectId, workspaceId);
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }
}
