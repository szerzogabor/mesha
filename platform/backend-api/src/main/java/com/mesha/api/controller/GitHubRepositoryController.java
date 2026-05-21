package com.mesha.api.controller;

import com.mesha.api.dto.ConnectRepositoryRequest;
import com.mesha.api.dto.GitHubRepositoryDto;
import com.mesha.api.service.GitHubAppService;
import com.mesha.api.service.GitHubRepositoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/github/repositories")
public class GitHubRepositoryController {

    private final GitHubRepositoryService repositoryService;
    private final GitHubAppService appService;

    public GitHubRepositoryController(GitHubRepositoryService repositoryService,
                                      GitHubAppService appService) {
        this.repositoryService = repositoryService;
        this.appService = appService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<GitHubRepositoryDto>> list(@PathVariable String workspaceId) {
        return ResponseEntity.ok(repositoryService.listForWorkspace(UUID.fromString(workspaceId)));
    }

    @GetMapping("/{repositoryId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<GitHubRepositoryDto> get(@PathVariable String workspaceId,
                                                   @PathVariable String repositoryId) {
        return ResponseEntity.ok(repositoryService.getById(UUID.fromString(repositoryId)));
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<GitHubRepositoryDto> connect(@PathVariable String workspaceId,
                                                       @Valid @RequestBody ConnectRepositoryRequest req) {
        GitHubRepositoryDto dto = GitHubRepositoryDto.from(
                appService.connectRepository(UUID.fromString(workspaceId),
                        req.installationId(), req.githubRepoId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{repositoryId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> disconnect(@PathVariable String workspaceId,
                                           @PathVariable String repositoryId) {
        repositoryService.disconnect(UUID.fromString(repositoryId));
        return ResponseEntity.noContent().build();
    }
}
