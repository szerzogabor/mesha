package com.mesha.api.controller;

import com.mesha.api.dto.CreateIssueLinkRequest;
import com.mesha.api.dto.IssueLinkDto;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.IssueLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/{issueId}/links")
public class IssueLinkController {

    private final IssueLinkService issueLinkService;

    public IssueLinkController(IssueLinkService issueLinkService) {
        this.issueLinkService = issueLinkService;
    }

    @GetMapping
    public ResponseEntity<List<IssueLinkDto>> list(@PathVariable UUID issueId) {
        List<IssueLinkDto> links = issueLinkService.listForIssue(issueId)
            .stream().map(IssueLinkDto::from).toList();
        return ResponseEntity.ok(links);
    }

    @PostMapping
    public ResponseEntity<IssueLinkDto> create(@PathVariable UUID issueId,
                                                @CurrentUser User user,
                                                @Valid @RequestBody CreateIssueLinkRequest req) {
        IssueLinkDto dto = IssueLinkDto.from(issueLinkService.create(issueId, req, user));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> delete(@PathVariable UUID issueId,
                                        @PathVariable UUID linkId) {
        issueLinkService.delete(issueId, linkId);
        return ResponseEntity.noContent().build();
    }
}
