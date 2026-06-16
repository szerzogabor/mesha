package com.mesha.api.service;

import com.mesha.api.ai.AIDraftContent;
import com.mesha.api.ai.AIOrchestrationService;
import com.mesha.api.ai.BlocksAIDraftGenerator;
import com.mesha.api.dto.ApproveDraftRequest;
import com.mesha.api.dto.CreateIssueRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.AIDraftRepository;
import com.mesha.api.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final BlocksAIDraftGenerator blocksGenerator;
    private final BlocksConfigService blocksConfigService;
    private final IssueService issueService;
    private final ActivityService activityService;

    public AIDraftService(AIDraftRepository draftRepository,
                          ProjectRepository projectRepository,
                          AIOrchestrationService orchestration,
                          BlocksAIDraftGenerator blocksGenerator,
                          BlocksConfigService blocksConfigService,
                          IssueService issueService,
                          ActivityService activityService) {
        this.draftRepository = draftRepository;
        this.projectRepository = projectRepository;
        this.orchestration = orchestration;
        this.blocksGenerator = blocksGenerator;
        this.blocksConfigService = blocksConfigService;
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

        log.info("AI draft generation started draftId={} projectId={}", draft.getId(), projectId);
        long aiStartMs = System.currentTimeMillis();
        try {
            UUID workspaceId = project.getWorkspace().getId();
            AIDraftContent content = blocksConfigService.isConnected(workspaceId)
                    ? blocksGenerator.generate(prompt, workspaceId)
                    : orchestration.generateDraft(prompt);
            log.info("AI draft generation completed draftId={} durationMs={}", draft.getId(), System.currentTimeMillis() - aiStartMs);
            draft.setStatus(AIDraftStatus.COMPLETED);
            draft.setGeneratedTitle(content.title());
            draft.setGeneratedDescription(content.description());
            draft.setAcceptanceCriteria(content.acceptanceCriteria());
            draft.setSuggestedLabels(content.suggestedLabels());
            draft.setPrioritySuggestion(content.prioritySuggestion());
            draft.setImplementationNotes(content.implementationNotes());
            draft.setScopeNotes(content.scopeNotes());
            draft.setOutOfScopeNotes(content.outOfScopeNotes());
        } catch (Exception e) {
            log.error("AI draft generation failed draftId={} durationMs={} error={}", draft.getId(), System.currentTimeMillis() - aiStartMs, e.getMessage(), e);
            draft.setStatus(AIDraftStatus.FAILED);
            draft.setErrorMessage(e.getMessage());
        }

        return draftRepository.save(draft);
    }

    public AIDraft getById(UUID draftId) {
        return draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }

    @Transactional
    public Issue approve(UUID draftId, ApproveDraftRequest req, User actor) {
        AIDraft draft = getById(draftId);
        if (draft.getStatus() == AIDraftStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft already approved");
        }
        if (draft.getStatus() == AIDraftStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft was rejected");
        }

        IssuePriority priority = req.priority() != null ? req.priority()
            : parsePriority(draft.getPrioritySuggestion());
        String status = req.status() != null ? req.status() : "BACKLOG";

        CreateIssueRequest createReq = new CreateIssueRequest(
            req.title(),
            req.description(),
            status,
            priority,
            null,
            null,
            null,
            null
        );

        Issue issue = issueService.create(draft.getProject().getId(), createReq, actor);
        activityService.record(issue, actor, ActivityEventType.ISSUE_CREATED_FROM_AI_DRAFT, null, draft.getId().toString());

        draft.setStatus(AIDraftStatus.APPROVED);
        draftRepository.save(draft);

        return issue;
    }

    @Transactional
    public void reject(UUID draftId) {
        AIDraft draft = getById(draftId);
        draft.setStatus(AIDraftStatus.REJECTED);
        draftRepository.save(draft);
    }

    // No @Transactional here: calls generate() which makes an external network call.
    // reject() and generate() each manage their own transactions.
    public AIDraft regenerate(UUID draftId, String newPrompt, User actor) {
        AIDraft existing = getById(draftId);
        String prompt = (newPrompt != null && !newPrompt.isBlank()) ? newPrompt : existing.getPrompt();
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
