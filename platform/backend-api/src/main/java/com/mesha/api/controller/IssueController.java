package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.*;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.IssueService;
import com.mesha.api.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    private final IssueService issueService;
    private final ActivityService activityService;

    public IssueController(IssueService issueService, ActivityService activityService) {
        this.issueService = issueService;
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<IssueDto>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<Issue> result = issueService.list(projectId, status, priority, assigneeId, search, page, size);
        return ResponseEntity.ok(PagedResponse.from(result, IssueDto::from));
    }

    @PostMapping
    public ResponseEntity<IssueDto> create(@PathVariable UUID projectId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody CreateIssueRequest req) {
        Issue issue = issueService.create(projectId, req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueDto.from(issue));
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<IssueDto> get(@PathVariable UUID projectId,
                                         @PathVariable UUID issueId) {
        return ResponseEntity.ok(IssueDto.from(issueService.getById(issueId)));
    }

    @PatchMapping("/{issueId}")
    public ResponseEntity<IssueDto> update(@PathVariable UUID projectId,
                                            @PathVariable UUID issueId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody UpdateIssueRequest req) {
        Issue updated = issueService.update(issueId, req, user);
        return ResponseEntity.ok(IssueDto.from(updated));
    }

    @DeleteMapping("/{issueId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                        @PathVariable UUID issueId) {
        issueService.delete(issueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{issueId}/activity")
    public ResponseEntity<List<ActivityEventDto>> getActivity(@PathVariable UUID projectId,
                                                               @PathVariable UUID issueId) {
        List<ActivityEventDto> events = activityService.getForIssue(issueId)
            .stream().map(ActivityEventDto::from).toList();
        return ResponseEntity.ok(events);
    }
}
