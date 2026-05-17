package com.mesha.api.controller;

import com.mesha.api.dto.CommentDto;
import com.mesha.api.dto.CreateCommentRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/{issueId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<List<CommentDto>> list(@PathVariable UUID issueId) {
        return ResponseEntity.ok(commentService.listThreaded(issueId));
    }

    @PostMapping
    public ResponseEntity<CommentDto> create(@PathVariable UUID issueId,
                                              @CurrentUser User user,
                                              @Valid @RequestBody CreateCommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommentDto.from(commentService.create(issueId, req, user)));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID issueId,
                                        @PathVariable UUID commentId,
                                        @CurrentUser User user) {
        commentService.delete(issueId, commentId, user);
        return ResponseEntity.noContent().build();
    }
}
