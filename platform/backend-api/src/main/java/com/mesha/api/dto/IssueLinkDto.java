package com.mesha.api.dto;

import com.mesha.api.model.IssueLink;
import com.mesha.api.model.IssueLinkType;
import java.time.Instant;
import java.util.UUID;

public record IssueLinkDto(
    UUID id,
    IssueLinkType linkType,
    LinkedIssueDto sourceIssue,
    LinkedIssueDto targetIssue,
    Instant createdAt
) {
    public record LinkedIssueDto(UUID id, String identifier, String title, String status) {
        public static LinkedIssueDto from(com.mesha.api.model.Issue issue) {
            String key = issue.getProject() != null ? issue.getProject().getKey() : null;
            Integer number = issue.getNumber();
            String identifier = (key != null && number != null) ? key + "-" + number : null;
            return new LinkedIssueDto(issue.getId(), identifier, issue.getTitle(), issue.getStatus());
        }
    }

    public static IssueLinkDto from(IssueLink link) {
        return new IssueLinkDto(
            link.getId(),
            link.getLinkType(),
            LinkedIssueDto.from(link.getSourceIssue()),
            LinkedIssueDto.from(link.getTargetIssue()),
            link.getCreatedAt()
        );
    }
}
