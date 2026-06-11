package com.mesha.api.service;

import com.mesha.api.dto.CreateIssueRequest;
import com.mesha.api.dto.UpdateIssueRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.*;
import com.mesha.api.repository.ProjectStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ActivityService activityService;
    private final ProjectStatusRepository projectStatusRepository;
    private final AutomationService automationService;

    public IssueService(IssueRepository issueRepository,
                        ProjectRepository projectRepository,
                        UserRepository userRepository,
                        LabelRepository labelRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        ActivityService activityService,
                        ProjectStatusRepository projectStatusRepository,
                        AutomationService automationService) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.labelRepository = labelRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.activityService = activityService;
        this.projectStatusRepository = projectStatusRepository;
        this.automationService = automationService;
    }

    @Transactional
    public Issue create(UUID projectId, CreateIssueRequest req, User actor) {
        log.debug("Creating issue projectId={} actorId={}", projectId, actor.getId());
        Project project = projectRepository.findByIdForUpdate(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        UUID workspaceId = project.getWorkspace().getId();

        Issue issue = new Issue();
        issue.setProject(project);
        issue.setTitle(req.title());
        issue.setDescription(req.description());

        if (req.status() != null) {
            validateStatus(projectId, req.status());
            issue.setStatus(req.status().toUpperCase());
        }
        if (req.priority() != null) issue.setPriority(req.priority());

        if (req.assigneeId() != null) {
            User assignee = userRepository.findById(req.assigneeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));
            if (!workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, req.assigneeId()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee is not a member of this workspace");
            }
            issue.setAssignee(assignee);
        }

        if (req.labelIds() != null && !req.labelIds().isEmpty()) {
            List<Label> labels = labelRepository.findAllByIdInAndWorkspace_Id(req.labelIds(), workspaceId);
            if (labels.size() != req.labelIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more labels do not belong to this workspace");
            }
            issue.setLabels(new ArrayList<>(labels));
        }

        if (req.agentType() != null && !req.agentType().isBlank()) {
            issue.setAgentType(req.agentType().toUpperCase());
            issue.setAgentLlm(req.agentLlm() != null ? req.agentLlm().toLowerCase() : null);
        }

        issue.setNumber(issueRepository.nextNumberForProject(projectId));
        issue = issueRepository.save(issue);
        activityService.record(issue, actor, ActivityEventType.ISSUE_CREATED, null, issue.getTitle());
        log.info("Issue created issueId={} projectId={} actorId={}", issue.getId(), projectId, actor.getId());
        return issue;
    }

    public Page<Issue> list(UUID projectId, String status, IssuePriority priority,
                             UUID assigneeId, String search, int page, int size) {
        long startMs = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);
        String normalizedSearch = search != null ? "%" + search.toLowerCase() + "%" : null;
        Page<Issue> result = issueRepository.findByProjectFiltered(projectId, status, priority, assigneeId, normalizedSearch, pageable);
        log.info("Listed issues projectId={} status={} priority={} count={} totalElements={} durationMs={}",
                projectId, status, priority, result.getNumberOfElements(), result.getTotalElements(), System.currentTimeMillis() - startMs);
        return result;
    }

    public Issue getById(UUID issueId) {
        long startMs = System.currentTimeMillis();
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        log.info("Fetched issue issueId={} projectId={} durationMs={}", issueId, issue.getProject().getId(), System.currentTimeMillis() - startMs);
        return issue;
    }

    @Transactional
    public Issue update(UUID issueId, UpdateIssueRequest req, User actor) {
        log.debug("Updating issue issueId={} actorId={}", issueId, actor.getId());
        Issue issue = getById(issueId);
        UUID workspaceId = issue.getProject().getWorkspace().getId();

        if (req.title() != null && !req.title().isBlank()) {
            String old = issue.getTitle();
            issue.setTitle(req.title());
            activityService.record(issue, actor, ActivityEventType.TITLE_CHANGED, old, req.title());
        }

        if (req.description() != null && !req.description().equals(issue.getDescription())) {
            String old = issue.getDescription();
            issue.setDescription(req.description());
            activityService.record(issue, actor, ActivityEventType.DESCRIPTION_CHANGED, old, req.description());
        }

        String statusChangedTo = null;
        if (req.status() != null && !req.status().equalsIgnoreCase(issue.getStatus())) {
            UUID projectId = issue.getProject().getId();
            validateStatus(projectId, req.status());
            String old = issue.getStatus();
            String newStatus = req.status().toUpperCase();
            issue.setStatus(newStatus);
            activityService.record(issue, actor, ActivityEventType.STATUS_CHANGED, old, newStatus);
            log.debug("Issue status changed issueId={} from={} to={}", issueId, old, newStatus);
            statusChangedTo = newStatus;
        }

        if (req.priority() != null && req.priority() != issue.getPriority()) {
            String old = issue.getPriority().name();
            issue.setPriority(req.priority());
            activityService.record(issue, actor, ActivityEventType.PRIORITY_CHANGED, old, req.priority().name());
        }

        if (Boolean.TRUE.equals(req.clearAssignee())) {
            String old = issue.getAssignee() != null ? issue.getAssignee().getId().toString() : null;
            issue.setAssignee(null);
            activityService.record(issue, actor, ActivityEventType.ASSIGNEE_CHANGED, old, null);
        } else if (req.assigneeId() != null) {
            User assignee = userRepository.findById(req.assigneeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));
            if (!workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, req.assigneeId()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee is not a member of this workspace");
            }
            String old = issue.getAssignee() != null ? issue.getAssignee().getId().toString() : null;
            issue.setAssignee(assignee);
            activityService.record(issue, actor, ActivityEventType.ASSIGNEE_CHANGED, old, assignee.getId().toString());
        }

        Set<UUID> addedLabelIds = null;
        if (req.labelIds() != null) {
            List<Label> labels = labelRepository.findAllByIdInAndWorkspace_Id(req.labelIds(), workspaceId);
            if (labels.size() != req.labelIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more labels do not belong to this workspace");
            }
            Set<UUID> existingLabelIds = issue.getLabels().stream().map(Label::getId).collect(Collectors.toSet());
            addedLabelIds = labels.stream()
                    .map(Label::getId)
                    .filter(id -> !existingLabelIds.contains(id))
                    .collect(Collectors.toSet());
            issue.setLabels(new ArrayList<>(labels));
        }

        if (Boolean.TRUE.equals(req.clearAgentAssignee())) {
            issue.setAgentType(null);
            issue.setAgentLlm(null);
        } else if (req.agentType() != null && !req.agentType().isBlank()) {
            issue.setAgentType(req.agentType().toUpperCase());
            issue.setAgentLlm(req.agentLlm() != null ? req.agentLlm().toLowerCase() : null);
        }

        Issue saved = issueRepository.save(issue);
        log.debug("Issue updated issueId={} actorId={}", issueId, actor.getId());

        if (statusChangedTo != null) {
            automationService.executeFor(AutomationTriggerType.STATUS_UPDATED, saved, statusChangedTo);
        }
        if (addedLabelIds != null) {
            for (UUID labelId : addedLabelIds) {
                automationService.executeFor(AutomationTriggerType.LABEL_ADDED, saved, labelId.toString());
            }
        }

        return saved;
    }

    @Transactional
    public void delete(UUID issueId) {
        log.info("Deleting issue issueId={}", issueId);
        Issue issue = getById(issueId);
        issueRepository.delete(issue);
        log.info("Issue deleted issueId={}", issueId);
    }

    private void validateStatus(UUID projectId, String status) {
        String normalized = status.toUpperCase();
        if (!projectStatusRepository.existsByProjectIdAndName(projectId, normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
    }
}
