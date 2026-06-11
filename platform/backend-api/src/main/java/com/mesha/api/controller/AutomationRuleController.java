package com.mesha.api.controller;

import com.mesha.api.dto.AutomationRuleDto;
import com.mesha.api.dto.CreateAutomationRuleRequest;
import com.mesha.api.dto.UpdateAutomationRuleRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.AutomationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/automations")
public class AutomationRuleController {

    private final AutomationService automationService;

    public AutomationRuleController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<AutomationRuleDto>> list(@PathVariable UUID projectId) {
        List<AutomationRuleDto> dtos = automationService.list(projectId)
            .stream().map(AutomationRuleDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<AutomationRuleDto> create(
            @PathVariable UUID projectId,
            @CurrentUser User user,
            @Valid @RequestBody CreateAutomationRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AutomationRuleDto.from(automationService.create(projectId, req, user)));
    }

    @PatchMapping("/{ruleId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<AutomationRuleDto> update(
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateAutomationRuleRequest req) {
        return ResponseEntity.ok(AutomationRuleDto.from(automationService.update(projectId, ruleId, req)));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId) {
        automationService.delete(projectId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
