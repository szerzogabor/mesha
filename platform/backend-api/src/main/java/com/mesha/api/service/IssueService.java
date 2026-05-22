package com.mesha.api.service;

import com.mesha.api.dto.CreateIssueRequest;
import com.mesha.api.dto.UpdateIssueRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.*;
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
import java.util.UUID;

@Service
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ActivityService activityService;

    public IssueService(IssueRepository issueRepository,
                        ProjectRepository projectRepository,
                        UserRepository userRepository,
                        LabelRepository labelRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        ActivityService activityService) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.labelRepository = labelRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.activityService = activityService;
    }

    @Transactional
    public Issue create(UUID projectId, CreateIssueRequest req, User actor) {
        log.debug("Creating issue projectId={} actorId={}", projectId, actor.getId());
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        UUID workspaceId = project.getWorkspace().getId();

        Issue issue = new Issue();
        issue.setProject(project);
        issue.setTitle(req.title());
        issue.setDescription(req.description());

        if (req.status() != null) issue.setStatus(req.status());
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

        issue = issueRepository.save(issue);
        activityService.record(issue, actor, ActivityEventType.ISSUE_CREATED, null, issue.getTitle());
        log.info("Issue created issueId={} projectId={} actorId={}", issue.getId(), projectId, actor.getId());
        return issue;
    }

    public Page<Issue> list(UUID projectId, IssueStatus status, IssuePriority priority,
                             UUID assigneeId, String search, int page, int size) {
        log.debug("Listing issues projectId={} status={} priority={} assigneeId={} page={} size={}",
                projectId, status, priority, assigneeId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        String normalizedSearch = search != null ? "%" + search.toLowerCase() + "%" : null;
        return issueRepository.findByProjectFiltered(projectId, status, priority, assigneeId, normalizedSearch, pageable);
    }

    public Issue getById(UUID issueId) {
        log.debug("Fetching issue issueId={}", issueId);
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
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

        if (req.status() != null && req.status() != issue.getStatus()) {
            String old = issue.getStatus().name();
            issue.setStatus(req.status());
            activityService.record(issue, actor, ActivityEventType.STATUS_CHANGED, old, req.status().name());
            log.debug("Issue status changed issueId={} from={} to={}", issueId, old, req.status().name());
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

        if (req.labelIds() != null) {
            List<Label> labels = labelRepository.findAllByIdInAndWorkspace_Id(req.labelIds(), workspaceId);
            if (labels.size() != req.labelIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more labels do not belong to this workspace");
            }
            issue.setLabels(new ArrayList<>(labels));
        }

        Issue saved = issueRepository.save(issue);
        log.debug("Issue updated issueId={} actorId={}", issueId, actor.getId());
        return saved;
    }

    @Transactional
    public void delete(UUID issueId) {
        log.info("Deleting issue issueId={}", issueId);
        Issue issue = getById(issueId);
        issueRepository.delete(issue);
        log.info("Issue deleted issueId={}", issueId);
    }
}
