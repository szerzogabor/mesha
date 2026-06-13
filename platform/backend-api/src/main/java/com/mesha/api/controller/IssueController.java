package com.mesha.api.controller;

import com.mesha.api.dto.*;
import com.mesha.api.model.*;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ActivityService;
import com.mesha.api.service.IssueSseService;
import com.mesha.api.service.IssueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    private final IssueService issueService;
    private final ActivityService activityService;
    private final IssueSseService issueSseService;
    private final GitHubPullRequestRepository gitHubPullRequestRepository;

    public IssueController(IssueService issueService, ActivityService activityService,
                           IssueSseService issueSseService,
                           GitHubPullRequestRepository gitHubPullRequestRepository) {
        this.issueService = issueService;
        this.activityService = activityService;
        this.issueSseService = issueSseService;
        this.gitHubPullRequestRepository = gitHubPullRequestRepository;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<IssueDto>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<Issue> result = issueService.list(projectId, status, priority, assigneeId, search, page, size);
        List<UUID> issueIds = result.getContent().stream().map(Issue::getId).toList();
        Map<UUID, GitHubPullRequest> lastPrByIssueId = issueIds.isEmpty()
                ? Map.of()
                : gitHubPullRequestRepository.findLatestByIssueIds(issueIds).stream()
                    .collect(Collectors.toMap(
                        pr -> pr.getBlocksSession().getIssue().getId(),
                        pr -> pr,
                        (a, b) -> a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a : b
                    ));
        return ResponseEntity.ok(PagedResponse.from(result, i -> IssueDto.from(i, lastPrByIssueId.get(i.getId()))));
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> create(@PathVariable UUID projectId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody CreateIssueRequest req) {
        Issue issue = issueService.create(projectId, req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueDto.from(issue));
    }

    @GetMapping("/{issueId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
    public ResponseEntity<IssueDto> get(@PathVariable UUID projectId,
                                         @PathVariable UUID issueId) {
        Issue issue = issueService.getById(issueId);
        GitHubPullRequest lastPr = gitHubPullRequestRepository
                .findFirstByBlocksSession_Issue_IdOrderByUpdatedAtDesc(issueId)
                .orElse(null);
        return ResponseEntity.ok(IssueDto.from(issue, lastPr));
    }

    @PatchMapping("/{issueId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueDto> update(@PathVariable UUID projectId,
                                            @PathVariable UUID issueId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody UpdateIssueRequest req) {
        Issue updated = issueService.update(issueId, req, user);
        return ResponseEntity.ok(IssueDto.from(updated));
    }

    @DeleteMapping("/{issueId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                        @PathVariable UUID issueId) {
        issueService.delete(issueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{issueId}/activity")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ActivityEventDto>> getActivity(@PathVariable UUID projectId,
                                                               @PathVariable UUID issueId) {
        List<ActivityEventDto> events = activityService.getForIssue(issueId)
            .stream().map(ActivityEventDto::from).toList();
        return ResponseEntity.ok(events);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public SseEmitter stream(@PathVariable UUID projectId) {
        return issueSseService.subscribe(projectId);
    }
}
