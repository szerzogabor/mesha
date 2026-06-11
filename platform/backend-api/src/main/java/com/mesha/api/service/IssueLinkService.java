package com.mesha.api.service;

import com.mesha.api.dto.CreateIssueLinkRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.IssueLinkRepository;
import com.mesha.api.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class IssueLinkService {

    private static final Logger log = LoggerFactory.getLogger(IssueLinkService.class);

    private final IssueLinkRepository issueLinkRepository;
    private final IssueRepository issueRepository;

    public IssueLinkService(IssueLinkRepository issueLinkRepository, IssueRepository issueRepository) {
        this.issueLinkRepository = issueLinkRepository;
        this.issueRepository = issueRepository;
    }

    public List<IssueLink> listForIssue(UUID issueId) {
        return issueLinkRepository.findAllByIssueId(issueId);
    }

    @Transactional
    public IssueLink create(UUID sourceIssueId, CreateIssueLinkRequest req, User actor) {
        if (sourceIssueId.equals(req.targetIssueId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot link an issue to itself");
        }

        Issue sourceIssue = issueRepository.findById(sourceIssueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source issue not found"));
        Issue targetIssue = issueRepository.findById(req.targetIssueId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target issue not found"));

        if (issueLinkRepository.existsBySourceIssueIdAndTargetIssueIdAndLinkType(
                sourceIssueId, req.targetIssueId(), req.linkType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Link already exists");
        }

        IssueLink link = new IssueLink();
        link.setSourceIssue(sourceIssue);
        link.setTargetIssue(targetIssue);
        link.setLinkType(req.linkType());
        link.setCreatedBy(actor);

        IssueLink saved = issueLinkRepository.save(link);
        log.info("Issue link created id={} type={} source={} target={}", saved.getId(), req.linkType(), sourceIssueId, req.targetIssueId());
        return saved;
    }

    @Transactional
    public void delete(UUID issueId, UUID linkId) {
        IssueLink link = issueLinkRepository.findById(linkId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));

        boolean isParticipant = link.getSourceIssue().getId().equals(issueId)
            || link.getTargetIssue().getId().equals(issueId);
        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Link does not belong to this issue");
        }

        issueLinkRepository.delete(link);
        log.info("Issue link deleted id={}", linkId);
    }
}
