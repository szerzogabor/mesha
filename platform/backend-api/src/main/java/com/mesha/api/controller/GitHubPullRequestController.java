package com.mesha.api.controller;

import com.mesha.api.dto.GitHubPullRequestDto;
import com.mesha.api.service.GitHubPullRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/github/repositories/{repositoryId}/pull-requests")
public class GitHubPullRequestController {

    private final GitHubPullRequestService prService;

    public GitHubPullRequestController(GitHubPullRequestService prService) {
        this.prService = prService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<GitHubPullRequestDto>> list(@PathVariable String workspaceId,
                                                           @PathVariable String repositoryId) {
        return ResponseEntity.ok(prService.listForRepository(UUID.fromString(repositoryId)));
    }

    @GetMapping("/{prId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<GitHubPullRequestDto> get(@PathVariable String workspaceId,
                                                    @PathVariable String repositoryId,
                                                    @PathVariable String prId) {
        return ResponseEntity.ok(prService.getById(UUID.fromString(prId)));
    }

    @PostMapping("/sync")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<GitHubPullRequestDto>> sync(@PathVariable String workspaceId,
                                                           @PathVariable String repositoryId) {
        return ResponseEntity.ok(prService.syncPullRequests(UUID.fromString(repositoryId)));
    }
}
