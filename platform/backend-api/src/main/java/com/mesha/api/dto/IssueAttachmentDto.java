package com.mesha.api.dto;

import com.mesha.api.model.IssueAttachment;

import java.time.Instant;
import java.util.UUID;

public record IssueAttachmentDto(
        UUID id,
        UUID issueId,
        String fileName,
        String contentType,
        long fileSize,
        String uploadedByName,
        Instant createdAt
) {
    public static IssueAttachmentDto from(IssueAttachment a) {
        String uploaderName = null;
        if (a.getUploadedBy() != null) {
            String name = a.getUploadedBy().getName();
            uploaderName = (name != null && !name.isBlank()) ? name : a.getUploadedBy().getEmail();
        }
        return new IssueAttachmentDto(
                a.getId(),
                a.getIssue().getId(),
                a.getFileName(),
                a.getContentType(),
                a.getFileSize(),
                uploaderName,
                a.getCreatedAt()
        );
    }
}
