package com.mesha.api.controller;

import com.mesha.api.dto.CommentDto;
import com.mesha.api.dto.CreateCommentRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.CommentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/{issueId}/comments")
public class CommentController {

    private static final Logger log = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<List<CommentDto>> list(@PathVariable UUID issueId) {
        log.debug("Listing comments issueId={}", issueId);
        return ResponseEntity.ok(commentService.listThreaded(issueId));
    }

    @PostMapping
    public ResponseEntity<CommentDto> create(@PathVariable UUID issueId,
                                              @CurrentUser User user,
                                              @Valid @RequestBody CreateCommentRequest req) {
        log.debug("Creating comment issueId={} userId={}", issueId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommentDto.from(commentService.create(issueId, req, user)));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID issueId,
                                        @PathVariable UUID commentId,
                                        @CurrentUser User user) {
        log.debug("Deleting comment commentId={} issueId={} userId={}", commentId, issueId, user.getId());
        commentService.delete(issueId, commentId, user);
        return ResponseEntity.noContent().build();
    }
}
