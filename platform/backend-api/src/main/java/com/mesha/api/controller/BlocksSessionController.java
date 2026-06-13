package com.mesha.api.controller;

import com.mesha.api.dto.BlocksMessageDto;
import com.mesha.api.dto.BlocksSessionDto;
import com.mesha.api.dto.SendMessageRequest;
import com.mesha.api.dto.StartSessionRequest;
import com.mesha.api.dto.UpdateBlocksSessionRequest;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.User;
import java.util.List;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.BlocksMessageService;
import com.mesha.api.service.BlocksSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
        List<GitHubPullRequest> linkedPrs = gitHubPullRequestRepository
                .findAllByBlocksSessionId(session.getId());
        if (linkedPrs.isEmpty() && session.getBranchName() != null) {
            linkedPrs = gitHubPullRequestRepository.findBySourceBranch(session.getBranchName());
        }
        return BlocksSessionDto.from(session, linkedPrs);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> assign(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @CurrentUser User user,
            @RequestBody(required = false) StartSessionRequest req) {
        String instructions = req != null ? req.instructions() : null;
        log.info("Assigning issue to Blocks issueId={} projectId={} userId={} hasInstructions={}",
                issueId, projectId, user.getId(), instructions != null && !instructions.isBlank());
        BlocksSession session = blocksSessionService.assignToBlocks(issueId, user, instructions);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BlocksSessionDto>> list(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        List<BlocksSessionDto> sessions = blocksSessionService.getSessionsForIssue(issueId)
            .stream().map(this::toDto).toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
    public ResponseEntity<BlocksSessionDto> getActive(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(toDto(blocksSessionService.getActiveSessionForIssue(issueId)));
    }

    @GetMapping("/{sessionId}")
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BlocksMessageDto>> getMessages(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId) {
        List<BlocksMessageDto> messages = blocksMessageService.getMessagesForSession(sessionId)
            .stream().map(BlocksMessageDto::from).toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}/messages")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksMessageDto> sendMessage(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId,
            @CurrentUser User user,
            @RequestBody SendMessageRequest req) {
        log.info("User sending message to session sessionId={} issueId={} userId={}", sessionId, issueId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BlocksMessageDto.from(blocksMessageService.addUserMessage(sessionId, req.content())));
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
