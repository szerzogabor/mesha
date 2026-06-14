package com.mesha.api.controller;

import com.mesha.api.dto.AssignAgentRequest;
import com.mesha.api.dto.IssueAgentDto;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.model.User;
import com.mesha.api.service.IssueAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/issues/{issueId}/agents")
public class IssueAgentController {

    private final IssueAgentService issueAgentService;

    public IssueAgentController(IssueAgentService issueAgentService) {
        this.issueAgentService = issueAgentService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<IssueAgentDto>> list(@PathVariable UUID projectId,
                                                     @PathVariable UUID issueId) {
        List<IssueAgentDto> agents = issueAgentService.listByIssue(issueId)
            .stream().map(IssueAgentDto::from).toList();
        return ResponseEntity.ok(agents);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueAgentDto> assign(@PathVariable UUID projectId,
                                                 @PathVariable UUID issueId,
                                                 @CurrentUser User user,
                                                 @Valid @RequestBody AssignAgentRequest req) {
        IssueAgentDto assignment = IssueAgentDto.from(
            issueAgentService.assign(issueId, req.agentDefinitionId(), user));
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @DeleteMapping("/{agentDefinitionId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<Void> unassign(@PathVariable UUID projectId,
                                          @PathVariable UUID issueId,
                                          @PathVariable UUID agentDefinitionId) {
        issueAgentService.unassign(issueId, agentDefinitionId);
        return ResponseEntity.noContent().build();
    }
}
