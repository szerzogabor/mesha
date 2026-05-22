package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.*;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.IssueService;
import com.mesha.api.service.ActivityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    private static final Logger log = LoggerFactory.getLogger(IssueController.class);

    private final IssueService issueService;
    private final ActivityService activityService;

    public IssueController(IssueService issueService, ActivityService activityService) {
        this.issueService = issueService;
        this.activityService = activityService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<PagedResponse<IssueDto>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.debug("Listing issues projectId={} status={} priority={} page={} size={}",
                projectId, status, priority, page, size);
        Page<Issue> result = issueService.list(projectId, status, priority, assigneeId, search, page, size);
        return ResponseEntity.ok(PagedResponse.from(result, IssueDto::from));
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> create(@PathVariable UUID projectId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody CreateIssueRequest req) {
        log.debug("Creating issue projectId={} userId={}", projectId, user.getId());
        Issue issue = issueService.create(projectId, req, user);
        log.debug("Issue created issueId={} projectId={}", issue.getId(), projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueDto.from(issue));
    }

    @GetMapping("/{issueId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> get(@PathVariable UUID projectId,
                                         @PathVariable UUID issueId) {
        log.debug("Fetching issue issueId={} projectId={}", issueId, projectId);
        return ResponseEntity.ok(IssueDto.from(issueService.getById(issueId)));
    }

    @PatchMapping("/{issueId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> update(@PathVariable UUID projectId,
                                            @PathVariable UUID issueId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody UpdateIssueRequest req) {
        log.debug("Updating issue issueId={} projectId={} userId={}", issueId, projectId, user.getId());
        Issue updated = issueService.update(issueId, req, user);
        return ResponseEntity.ok(IssueDto.from(updated));
    }

    @DeleteMapping("/{issueId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                        @PathVariable UUID issueId) {
        log.info("Deleting issue issueId={} projectId={}", issueId, projectId);
        issueService.delete(issueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{issueId}/activity")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<ActivityEventDto>> getActivity(@PathVariable UUID projectId,
                                                               @PathVariable UUID issueId) {
        log.debug("Fetching activity issueId={} projectId={}", issueId, projectId);
        List<ActivityEventDto> events = activityService.getForIssue(issueId)
            .stream().map(ActivityEventDto::from).toList();
        return ResponseEntity.ok(events);
    }
}
