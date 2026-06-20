package com.mesha.api.service;

import com.mesha.api.model.Issue;
import com.mesha.api.model.IssueAttachment;
import com.mesha.api.model.User;
import com.mesha.api.repository.IssueAttachmentRepository;
import com.mesha.api.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class IssueAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(IssueAttachmentService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final IssueAttachmentRepository attachmentRepository;
    private final IssueRepository issueRepository;

    public IssueAttachmentService(IssueAttachmentRepository attachmentRepository,
                                   IssueRepository issueRepository) {
        this.attachmentRepository = attachmentRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional
    public IssueAttachment upload(UUID issueId, MultipartFile file, User uploader) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds maximum allowed size of 10 MB");
        }

        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        try {
            IssueAttachment attachment = new IssueAttachment();
            attachment.setIssue(issue);
            attachment.setUploadedBy(uploader);
            attachment.setFileName(sanitizeFileName(file.getOriginalFilename()));
            attachment.setContentType(resolveContentType(file));
            attachment.setFileSize(file.getSize());
            attachment.setContent(file.getBytes());

            IssueAttachment saved = attachmentRepository.save(attachment);
            log.info("attachment_uploaded issueId={} attachmentId={} fileName={} sizeBytes={}",
                    issueId, saved.getId(), saved.getFileName(), saved.getFileSize());
            return saved;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read uploaded file: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<IssueAttachment> list(UUID issueId) {
        return attachmentRepository.findAllByIssueIdOrderByCreatedAtAsc(issueId);
    }

    @Transactional(readOnly = true)
    public IssueAttachment getById(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }

    @Transactional
    public void delete(UUID attachmentId, UUID issueId) {
        IssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        if (!attachment.getIssue().getId().equals(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        attachmentRepository.delete(attachment);
        log.info("attachment_deleted attachmentId={} issueId={}", attachmentId, issueId);
    }

    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) return "file";
        // Keep only the last path segment (some browsers send full paths)
        String name = original.replaceAll(".*[/\\\\]", "");
        return name.isBlank() ? "file" : name;
    }

    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        return (ct != null && !ct.isBlank()) ? ct : "application/octet-stream";
    }
}
