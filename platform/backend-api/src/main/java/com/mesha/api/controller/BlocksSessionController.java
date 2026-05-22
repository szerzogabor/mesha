package com.mesha.api.controller;

import com.mesha.api.dto.BlocksSessionDto;
import com.mesha.api.dto.UpdateBlocksSessionRequest;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
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

    public BlocksSessionController(BlocksSessionService blocksSessionService) {
        this.blocksSessionService = blocksSessionService;
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> assign(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @CurrentUser User user) {
        log.info("Assigning issue to Blocks issueId={} projectId={} userId={}", issueId, projectId, user.getId());
        BlocksSession session = blocksSessionService.assignToBlocks(issueId, user);
        log.info("Blocks session created sessionId={} issueId={} userId={}", session.getId(), issueId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(BlocksSessionDto.from(session));
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<BlocksSessionDto>> list(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        log.debug("Listing Blocks sessions issueId={} projectId={}", issueId, projectId);
        List<BlocksSessionDto> sessions = blocksSessionService.getSessionsForIssue(issueId)
            .stream().map(BlocksSessionDto::from).toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/active")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> getActive(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        log.debug("Fetching active Blocks session issueId={} projectId={}", issueId, projectId);
        return ResponseEntity.ok(BlocksSessionDto.from(blocksSessionService.getActiveSessionForIssue(issueId)));
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> get(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId) {
        log.debug("Fetching Blocks session sessionId={} issueId={}", sessionId, issueId);
        return ResponseEntity.ok(BlocksSessionDto.from(blocksSessionService.getById(sessionId)));
    }

    @PatchMapping("/{sessionId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<BlocksSessionDto> update(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID sessionId,
            @CurrentUser User user,
            @RequestBody UpdateBlocksSessionRequest req) {
        log.debug("Updating Blocks session sessionId={} issueId={} userId={}", sessionId, issueId, user.getId());
        BlocksSession session = blocksSessionService.updateSession(sessionId, req, user);
        return ResponseEntity.ok(BlocksSessionDto.from(session));
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
        log.info("Blocks session cancelled sessionId={} issueId={}", sessionId, issueId);
        return ResponseEntity.ok(BlocksSessionDto.from(session));
    }
}
