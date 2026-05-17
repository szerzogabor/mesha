package com.mesha.api.dto;

import com.mesha.api.model.Comment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentDto(
    UUID id,
    UUID issueId,
    String body,
    UserDto author,
    UUID parentId,
    List<CommentDto> replies,
    Instant createdAt,
    Instant updatedAt
) {
    public static CommentDto from(Comment c) {
        return new CommentDto(
            c.getId(),
            c.getIssue().getId(),
            c.getBody(),
            c.getAuthor() != null ? UserDto.from(c.getAuthor()) : null,
            c.getParent() != null ? c.getParent().getId() : null,
            List.of(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }

    public static CommentDto fromWithReplies(Comment c, List<CommentDto> replies) {
        return new CommentDto(
            c.getId(),
            c.getIssue().getId(),
            c.getBody(),
            c.getAuthor() != null ? UserDto.from(c.getAuthor()) : null,
            c.getParent() != null ? c.getParent().getId() : null,
            replies,
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
