package com.mesha.api.controller;

import com.mesha.api.dto.CreateLabelRequest;
import com.mesha.api.dto.LabelDto;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.model.User;
import com.mesha.api.service.LabelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/labels")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<LabelDto>> list(@PathVariable String workspaceId) {
        List<LabelDto> labels = labelService.listByWorkspace(UUID.fromString(workspaceId))
            .stream().map(LabelDto::from).toList();
        return ResponseEntity.ok(labels);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<LabelDto> create(@PathVariable String workspaceId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody CreateLabelRequest req) {
        LabelDto label = LabelDto.from(labelService.create(UUID.fromString(workspaceId), req));
        return ResponseEntity.status(HttpStatus.CREATED).body(label);
    }

    @DeleteMapping("/{labelId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId)")
    public ResponseEntity<Void> delete(@PathVariable String workspaceId,
                                        @PathVariable UUID labelId) {
        labelService.delete(UUID.fromString(workspaceId), labelId);
        return ResponseEntity.noContent().build();
    }
}
