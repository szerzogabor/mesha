package com.mesha.api.controller;

import com.mesha.api.dto.CreateTicketRuleRequest;
import com.mesha.api.dto.TicketRuleDto;
import com.mesha.api.dto.UpdateTicketRuleRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.TicketRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/ticket-rules")
public class TicketRuleController {

    private final TicketRuleService ticketRuleService;

    public TicketRuleController(TicketRuleService ticketRuleService) {
        this.ticketRuleService = ticketRuleService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<TicketRuleDto>> list(@PathVariable UUID projectId) {
        List<TicketRuleDto> dtos = ticketRuleService.list(projectId)
            .stream().map(TicketRuleDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<TicketRuleDto> create(
            @PathVariable UUID projectId,
            @CurrentUser User user,
            @Valid @RequestBody CreateTicketRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TicketRuleDto.from(ticketRuleService.create(projectId, req, user)));
    }

    @PatchMapping("/{ruleId}")
    @Transactional
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<TicketRuleDto> update(
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateTicketRuleRequest req) {
        return ResponseEntity.ok(TicketRuleDto.from(ticketRuleService.update(projectId, ruleId, req)));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("@workspaceSecurity.isProjectAdminOrAbove(authentication, #projectId.toString())")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId) {
        ticketRuleService.delete(projectId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
