package com.mesha.api.controller;

import com.mesha.api.dto.AIDraftDto;
import com.mesha.api.dto.ApproveDraftRequest;
import com.mesha.api.dto.GenerateDraftRequest;
import com.mesha.api.dto.IssueDto;
import com.mesha.api.model.AIDraft;
import com.mesha.api.model.Issue;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.AIDraftService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-drafts")
public class AIDraftController {

    private static final Logger log = LoggerFactory.getLogger(AIDraftController.class);

    private final AIDraftService draftService;

    public AIDraftController(AIDraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<AIDraftDto> generate(
            @PathVariable UUID projectId,
            @CurrentUser User user,
            @Valid @RequestBody GenerateDraftRequest req) {
        log.info("Generating AI draft projectId={} userId={}", projectId, user.getId());
        AIDraft draft = draftService.generate(projectId, req.prompt(), user);
        log.info("AI draft generated draftId={} projectId={} userId={}", draft.getId(), projectId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(AIDraftDto.from(draft));
    }

    @GetMapping("/{draftId}")
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<AIDraftDto> get(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId) {
        log.debug("Fetching AI draft draftId={} projectId={}", draftId, projectId);
        return ResponseEntity.ok(AIDraftDto.from(draftService.getById(draftId)));
    }

    @PostMapping("/{draftId}/approve")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> approve(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId,
            @CurrentUser User user,
            @Valid @RequestBody ApproveDraftRequest req) {
        log.info("Approving AI draft draftId={} projectId={} userId={}", draftId, projectId, user.getId());
        Issue issue = draftService.approve(draftId, req, user);
        log.info("AI draft approved issueId={} draftId={} userId={}", issue.getId(), draftId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueDto.from(issue));
    }

    @DeleteMapping("/{draftId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<Void> reject(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId) {
        log.info("Rejecting AI draft draftId={} projectId={}", draftId, projectId);
        draftService.reject(draftId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{draftId}/regenerate")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<AIDraftDto> regenerate(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId,
            @CurrentUser User user,
            @RequestBody(required = false) GenerateDraftRequest req) {
        String newPrompt = req != null ? req.prompt() : null;
        log.info("Regenerating AI draft draftId={} projectId={} userId={} hasNewPrompt={}",
                draftId, projectId, user.getId(), newPrompt != null);
        AIDraft draft = draftService.regenerate(draftId, newPrompt, user);
        log.info("AI draft regenerated newDraftId={} previousDraftId={}", draft.getId(), draftId);
        return ResponseEntity.status(HttpStatus.CREATED).body(AIDraftDto.from(draft));
    }
}
