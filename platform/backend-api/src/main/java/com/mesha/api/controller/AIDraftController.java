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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-drafts")
public class AIDraftController {

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
        AIDraft draft = draftService.generate(projectId, req.prompt(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(AIDraftDto.from(draft));
    }

    @GetMapping("/{draftId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<AIDraftDto> get(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId) {
        return ResponseEntity.ok(AIDraftDto.from(draftService.getById(draftId)));
    }

    @PostMapping("/{draftId}/approve")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> approve(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId,
            @CurrentUser User user,
            @Valid @RequestBody ApproveDraftRequest req) {
        Issue issue = draftService.approve(draftId, req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueDto.from(issue));
    }

    @DeleteMapping("/{draftId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<Void> reject(
            @PathVariable UUID projectId,
            @PathVariable UUID draftId) {
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
        AIDraft draft = draftService.regenerate(draftId, newPrompt, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(AIDraftDto.from(draft));
    }
}
