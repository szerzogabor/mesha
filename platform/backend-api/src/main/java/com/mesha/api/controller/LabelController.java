package com.mesha.api.controller;

import com.mesha.api.dto.CreateLabelRequest;
import com.mesha.api.dto.LabelDto;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.model.User;
import com.mesha.api.service.LabelService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/labels")
public class LabelController {

    private static final Logger log = LoggerFactory.getLogger(LabelController.class);

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId.toString())")
    public ResponseEntity<List<LabelDto>> list(@PathVariable UUID workspaceId) {
        log.debug("Listing labels workspaceId={}", workspaceId);
        List<LabelDto> labels = labelService.listByWorkspace(workspaceId)
            .stream().map(LabelDto::from).toList();
        return ResponseEntity.ok(labels);
    }

    @PostMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId.toString())")
    public ResponseEntity<LabelDto> create(@PathVariable UUID workspaceId,
                                            @CurrentUser User user,
                                            @Valid @RequestBody CreateLabelRequest req) {
        log.debug("Creating label workspaceId={} userId={} name={}", workspaceId, user.getId(), req.name());
        LabelDto label = LabelDto.from(labelService.create(workspaceId, req));
        log.debug("Label created labelId={} workspaceId={}", label.id(), workspaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(label);
    }

    @DeleteMapping("/{labelId}")
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId.toString())")
    public ResponseEntity<Void> delete(@PathVariable UUID workspaceId,
                                        @PathVariable UUID labelId) {
        log.debug("Deleting label labelId={} workspaceId={}", labelId, workspaceId);
        labelService.delete(workspaceId, labelId);
        return ResponseEntity.noContent().build();
    }
}
