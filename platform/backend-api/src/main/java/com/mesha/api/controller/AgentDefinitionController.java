package com.mesha.api.controller;

import com.mesha.api.dto.AgentDefinitionDto;
import com.mesha.api.dto.CreateAgentDefinitionRequest;
import com.mesha.api.dto.UpdateAgentDefinitionRequest;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.model.User;
import com.mesha.api.service.AgentDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/agents")
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;

    public AgentDefinitionController(AgentDefinitionService agentDefinitionService) {
        this.agentDefinitionService = agentDefinitionService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<AgentDefinitionDto>> list(@PathVariable String workspaceId) {
        List<AgentDefinitionDto> agents = agentDefinitionService.listByWorkspace(UUID.fromString(workspaceId))
            .stream().map(AgentDefinitionDto::from).toList();
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/active")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<AgentDefinitionDto>> listActive(@PathVariable String workspaceId) {
        List<AgentDefinitionDto> agents = agentDefinitionService.listActiveByWorkspace(UUID.fromString(workspaceId))
            .stream().map(AgentDefinitionDto::from).toList();
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{agentId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<AgentDefinitionDto> get(@PathVariable String workspaceId,
                                                   @PathVariable UUID agentId) {
        return ResponseEntity.ok(AgentDefinitionDto.from(agentDefinitionService.getById(UUID.fromString(workspaceId), agentId)));
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<AgentDefinitionDto> create(@PathVariable String workspaceId,
                                                      @CurrentUser User user,
                                                      @Valid @RequestBody CreateAgentDefinitionRequest req) {
        AgentDefinitionDto agent = AgentDefinitionDto.from(
            agentDefinitionService.create(UUID.fromString(workspaceId), req));
        return ResponseEntity.status(HttpStatus.CREATED).body(agent);
    }

    @PutMapping("/{agentId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<AgentDefinitionDto> update(@PathVariable String workspaceId,
                                                      @PathVariable UUID agentId,
                                                      @Valid @RequestBody UpdateAgentDefinitionRequest req) {
        AgentDefinitionDto agent = AgentDefinitionDto.from(
            agentDefinitionService.update(UUID.fromString(workspaceId), agentId, req));
        return ResponseEntity.ok(agent);
    }

    @DeleteMapping("/{agentId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> delete(@PathVariable String workspaceId,
                                        @PathVariable UUID agentId) {
        agentDefinitionService.delete(UUID.fromString(workspaceId), agentId);
        return ResponseEntity.noContent().build();
    }
}
