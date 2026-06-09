package com.mesha.api.controller;

import com.mesha.api.dto.BlocksMessageDto;
import com.mesha.api.dto.BlocksSessionDto;
import com.mesha.api.dto.UpdateBlocksSessionRequest;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.User;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.BlocksMessageService;
import com.mesha.api.service.BlocksSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/issues/{issueId}/blocks-sessions")
public class BlocksSessionController {

    private static final Logger log = LoggerFactory.getLogger(BlocksSessionController.class);

    private final BlocksSessionService blocksSessionService;
    private final BlocksMessageService blocksMessageService;
    private final GitHubPullRequestRepository gitHubPullRequestRepository;

    public BlocksSessionController(BlocksSessionService blocksSessionService,
                                   BlocksMessageService blocksMessageService,
                                   GitHubPullRequestRepository gitHubPullRequestRepository) {
        this.blocksSessionService = blocksSessionService;
        this.blocksMessageService = blocksMessageService;
        this.gitHubPullRequestRepository = gitHubPullRequestRepository;
    }

    private BlocksSessionDto toDto(BlocksSession session) {
        GitHubPullRequest linkedPr = gitHubPullRequestRepository
                .findByBlocksSessionId(session.getId()).orElse(null);
        return BlocksSessionDto.from(session, linkedPr);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> assign(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @CurrentUser User user) {
        log.info("Assigning issue to Blocks issueId={} projectId={} userId={}", issueId, projectId, user.getId());
        BlocksSession session = blocksSessionService.assignToBlocks(issueId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<BlocksSessionDto>> list(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        List<BlocksSessionDto> sessions = blocksSessionService.getSessionsForIssue(issueId)
            .stream().map(this::toDto).toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/active")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> getActive(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(toDto(blocksSessionService.getActiveSessionForIssue(issueId)));
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> get(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(toDto(blocksSessionService.getById(sessionId)));
    }

    @PatchMapping("/{sessionId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> update(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId,
            @CurrentUser User user,
            @RequestBody UpdateBlocksSessionRequest req) {
        BlocksSession session = blocksSessionService.updateSession(sessionId, req, user);
        return ResponseEntity.ok(toDto(session));
    }

    @GetMapping("/{sessionId}/messages")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<BlocksMessageDto>> getMessages(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId) {
        List<BlocksMessageDto> messages = blocksMessageService.getMessagesForSession(sessionId)
            .stream().map(BlocksMessageDto::from).toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}/cancel")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> cancel(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId,
            @CurrentUser User user) {
        log.info("Cancelling Blocks session sessionId={} issueId={} userId={}", sessionId, issueId, user.getId());
        BlocksSession session = blocksSessionService.cancelSession(sessionId, user);
        return ResponseEntity.ok(toDto(session));
    }
}
