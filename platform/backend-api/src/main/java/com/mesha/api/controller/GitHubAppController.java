package com.mesha.api.controller;

import com.mesha.api.dto.GitHubInstallationDto;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.GitHubAppService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/github")
public class GitHubAppController {

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
            @PathVariable Long installationId,
            @CurrentUser User user) {
        GitHubInstallationDto dto = GitHubInstallationDto.from(
                appService.registerInstallation(installationId, UUID.fromString(workspaceId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
