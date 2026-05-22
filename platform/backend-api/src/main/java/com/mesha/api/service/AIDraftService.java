package com.mesha.api.service;

import com.mesha.api.ai.AIDraftContent;
import com.mesha.api.ai.AIOrchestrationService;
import com.mesha.api.dto.ApproveDraftRequest;
import com.mesha.api.dto.CreateIssueRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.AIDraftRepository;
import com.mesha.api.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AIDraftService {

    private static final Logger log = LoggerFactory.getLogger(AIDraftService.class);

    private final AIDraftRepository draftRepository;
    private final ProjectRepository projectRepository;
    private final AIOrchestrationService orchestration;
    private final IssueService issueService;
    private final ActivityService activityService;

    public AIDraftService(AIDraftRepository draftRepository,
                          ProjectRepository projectRepository,
                          AIOrchestrationService orchestration,
                          IssueService issueService,
                          ActivityService activityService) {
        this.draftRepository = draftRepository;
        this.projectRepository = projectRepository;
        this.orchestration = orchestration;
        this.issueService = issueService;
        this.activityService = activityService;
    }

    // No @Transactional here: the AI network call must not hold a DB connection open.
    // Each draftRepository.save() call runs in its own implicit transaction.
    public AIDraft generate(UUID projectId, String prompt, User actor) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        AIDraft draft = new AIDraft();
        draft.setProject(project);
        draft.setCreatedBy(actor);
        draft.setPrompt(prompt);
        draft.setStatus(AIDraftStatus.PENDING);
        draft = draftRepository.save(draft);

        MDC.put("draftId", draft.getId().toString());
        long startMs = System.currentTimeMillis();
        try {
            log.info("ai_draft_generate_start draft_id={} project_id={} prompt_length={}",
                    draft.getId(), projectId, prompt.length());
            AIDraftContent content = orchestration.generateDraft(prompt);
            draft.setStatus(AIDraftStatus.COMPLETED);
            draft.setGeneratedTitle(content.title());
            draft.setGeneratedDescription(content.description());
            draft.setAcceptanceCriteria(content.acceptanceCriteria());
            draft.setSuggestedLabels(content.suggestedLabels());
            draft.setPrioritySuggestion(content.prioritySuggestion());
            draft.setImplementationNotes(content.implementationNotes());
            draft.setScopeNotes(content.scopeNotes());
            draft.setOutOfScopeNotes(content.outOfScopeNotes());
            log.info("ai_draft_generate_complete draft_id={} duration_ms={}",
                    draft.getId(), System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            log.error("ai_draft_generate_failed draft_id={} duration_ms={}",
                    draft.getId(), System.currentTimeMillis() - startMs, e);
            draft.setStatus(AIDraftStatus.FAILED);
            draft.setErrorMessage(e.getMessage());
        } finally {
            MDC.remove("draftId");
        }

        return draftRepository.save(draft);
    }

    public AIDraft getById(UUID draftId) {
        return draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }

    @Transactional
    public Issue approve(UUID draftId, ApproveDraftRequest req, User actor) {
        MDC.put("draftId", draftId.toString());
        try {
            AIDraft draft = getById(draftId);
            if (draft.getStatus() == AIDraftStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft already approved");
            }
            if (draft.getStatus() == AIDraftStatus.REJECTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft was rejected");
            }

            IssuePriority priority = req.priority() != null ? req.priority()
                : parsePriority(draft.getPrioritySuggestion());
            IssueStatus status = req.status() != null ? req.status() : IssueStatus.BACKLOG;

            CreateIssueRequest createReq = new CreateIssueRequest(
                req.title(),
                req.description(),
                status,
                priority,
                null,
                null
            );

            Issue issue = issueService.create(draft.getProject().getId(), createReq, actor);
            activityService.record(issue, actor, ActivityEventType.ISSUE_CREATED_FROM_AI_DRAFT, null, draft.getId().toString());

            draft.setStatus(AIDraftStatus.APPROVED);
            draftRepository.save(draft);

            log.info("ai_draft_approved draft_id={} issue_id={}", draftId, issue.getId());
            return issue;
        } finally {
            MDC.remove("draftId");
        }
    }

    @Transactional
    public void reject(UUID draftId) {
        MDC.put("draftId", draftId.toString());
        try {
            AIDraft draft = getById(draftId);
            draft.setStatus(AIDraftStatus.REJECTED);
            draftRepository.save(draft);
            log.info("ai_draft_rejected draft_id={}", draftId);
        } finally {
            MDC.remove("draftId");
        }
    }

    // No @Transactional here: calls generate() which makes an external network call.
    // reject() and generate() each manage their own transactions.
    public AIDraft regenerate(UUID draftId, String newPrompt, User actor) {
        AIDraft existing = getById(draftId);
        String prompt = (newPrompt != null && !newPrompt.isBlank()) ? newPrompt : existing.getPrompt();
        log.info("ai_draft_regenerate draft_id={} prompt_changed={}", draftId, newPrompt != null && !newPrompt.isBlank());
        reject(draftId);
        return generate(existing.getProject().getId(), prompt, actor);
    }

    private IssuePriority parsePriority(String suggestion) {
        if (suggestion == null) return IssuePriority.MEDIUM;
        try {
            return IssuePriority.valueOf(suggestion.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IssuePriority.MEDIUM;
        }
    }
}
