package com.mesha.api.controller;

import com.mesha.api.dto.AvailableRepositoryDto;
import com.mesha.api.dto.GitHubInstallationDto;
import com.mesha.api.service.GitHubAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/github")
public class GitHubAppController {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppController.class);

    private final GitHubAppService appService;

    public GitHubAppController(GitHubAppService appService) {
        this.appService = appService;
    }

    /**
     * Lists all GitHub App installations linked to this workspace.
     */
    @GetMapping("/installations")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<GitHubInstallationDto>> listInstallations(@PathVariable String workspaceId) {
        return ResponseEntity.ok(appService.listInstallations(UUID.fromString(workspaceId)));
    }

    /**
     * Called after the GitHub App installation redirect. Links the installation to the workspace.
     */
    @PostMapping("/installations/{installationId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<GitHubInstallationDto> registerInstallation(
            @PathVariable String workspaceId,
            @PathVariable Long installationId) {
        log.info("Registering GitHub App installation installationId={} workspaceId={}", installationId, workspaceId);
        GitHubInstallationDto dto = GitHubInstallationDto.from(
                appService.registerInstallation(installationId, UUID.fromString(workspaceId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lists repositories accessible to a specific installation from GitHub.
     * Used by the frontend to populate the repository selection dropdown.
     */
    @GetMapping("/installations/{installationId}/repositories")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<AvailableRepositoryDto>> listInstallationRepositories(
            @PathVariable String workspaceId,
            @PathVariable Long installationId) {
        return ResponseEntity.ok(
                appService.listAvailableRepositories(installationId, UUID.fromString(workspaceId)));
    }

    /**
     * Refreshes installation metadata, repository list, and permissions from GitHub.
     * Detaches any repositories that are no longer accessible to the installation.
     */
    @PostMapping("/installations/{id}/refresh")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<GitHubInstallationDto> refreshInstallation(
            @PathVariable String workspaceId,
            @PathVariable UUID id) {
        log.info("Refreshing installation id={} workspaceId={}", id, workspaceId);
        return ResponseEntity.ok(appService.refreshInstallation(id, UUID.fromString(workspaceId)));
    }
}
