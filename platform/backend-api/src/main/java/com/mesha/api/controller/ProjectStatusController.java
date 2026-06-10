package com.mesha.api.controller;

import com.mesha.api.dto.CreateProjectStatusRequest;
import com.mesha.api.dto.ProjectStatusDto;
import com.mesha.api.dto.ReorderProjectStatusesRequest;
import com.mesha.api.dto.UpdateProjectStatusRequest;
import com.mesha.api.service.ProjectStatusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/statuses")
public class ProjectStatusController {

    private final ProjectStatusService statusService;

    public ProjectStatusController(ProjectStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<ProjectStatusDto>> list(@PathVariable UUID projectId) {
        List<ProjectStatusDto> dtos = statusService.list(projectId)
            .stream().map(ProjectStatusDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<ProjectStatusDto> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectStatusRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProjectStatusDto.from(statusService.create(projectId, req)));
    }

    @PatchMapping("/{statusId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<ProjectStatusDto> update(
            @PathVariable UUID projectId,
            @PathVariable UUID statusId,
            @Valid @RequestBody UpdateProjectStatusRequest req) {
        return ResponseEntity.ok(ProjectStatusDto.from(statusService.update(projectId, statusId, req)));
    }

    @DeleteMapping("/{statusId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID statusId) {
        statusService.delete(projectId, statusId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<List<ProjectStatusDto>> reorder(
            @PathVariable UUID projectId,
            @Valid @RequestBody ReorderProjectStatusesRequest req) {
        List<ProjectStatusDto> dtos = statusService.reorder(projectId, req.statusIds())
            .stream().map(ProjectStatusDto::from).toList();
        return ResponseEntity.ok(dtos);
    }
}
