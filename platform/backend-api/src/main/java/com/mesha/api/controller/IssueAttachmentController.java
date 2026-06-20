package com.mesha.api.controller;

import com.mesha.api.dto.IssueAttachmentDto;
import com.mesha.api.model.IssueAttachment;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.IssueAttachmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/issues/{issueId}/attachments")
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    public IssueAttachmentController(IssueAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<IssueAttachmentDto> upload(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestParam("file") MultipartFile file,
            @CurrentUser User user) {
        IssueAttachment attachment = attachmentService.upload(issueId, file, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueAttachmentDto.from(attachment));
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<List<IssueAttachmentDto>> list(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        List<IssueAttachmentDto> dtos = attachmentService.list(issueId)
                .stream().map(IssueAttachmentDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID attachmentId) {
        IssueAttachment attachment = attachmentService.getById(attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + attachment.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(attachment.getContent());
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("@workspaceSecurity.isProjectMember(authentication, #projectId.toString())")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID attachmentId) {
        attachmentService.delete(attachmentId, issueId);
        return ResponseEntity.noContent().build();
    }
}
