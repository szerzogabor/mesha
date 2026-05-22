package com.mesha.api.controller;

import com.mesha.api.dto.ConnectRepositoryRequest;
import com.mesha.api.dto.GitHubRepositoryDto;
import com.mesha.api.service.GitHubAppService;
import com.mesha.api.service.GitHubRepositoryService;
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
@RequestMapping("/api/workspaces/{workspaceId}/github/repositories")
public class GitHubRepositoryController {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryController.class);

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
        log.debug("Listing repositories workspaceId={}", workspaceId);
        return ResponseEntity.ok(repositoryService.listForWorkspace(UUID.fromString(workspaceId)));
    }

    @GetMapping("/{repositoryId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<GitHubRepositoryDto> get(@PathVariable String workspaceId,
                                                   @PathVariable String repositoryId) {
        log.debug("Fetching repository repositoryId={} workspaceId={}", repositoryId, workspaceId);
        return ResponseEntity.ok(repositoryService.getById(UUID.fromString(repositoryId)));
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<GitHubRepositoryDto> connect(@PathVariable String workspaceId,
                                                       @Valid @RequestBody ConnectRepositoryRequest req) {
        log.info("Connecting repository workspaceId={} installationId={} githubRepoId={}",
                workspaceId, req.installationId(), req.githubRepoId());
        GitHubRepositoryDto dto = GitHubRepositoryDto.from(
                appService.connectRepository(UUID.fromString(workspaceId),
                        req.installationId(), req.githubRepoId()));
        log.info("Repository connected workspaceId={} repositoryId={}", workspaceId, dto.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{repositoryId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> disconnect(@PathVariable String workspaceId,
                                           @PathVariable String repositoryId) {
        log.info("Disconnecting repository repositoryId={} workspaceId={}", repositoryId, workspaceId);
        repositoryService.disconnect(UUID.fromString(repositoryId));
        return ResponseEntity.noContent().build();
    }
}
