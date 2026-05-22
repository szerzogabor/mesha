package com.mesha.api.service;

import com.mesha.api.dto.CommentDto;
import com.mesha.api.dto.CreateCommentRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.CommentRepository;
import com.mesha.api.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final ActivityService activityService;

    public CommentService(CommentRepository commentRepository,
                          IssueRepository issueRepository,
                          ActivityService activityService) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.activityService = activityService;
    }

    @Transactional
    public Comment create(UUID issueId, CreateCommentRequest req, User author) {
        log.debug("Creating comment issueId={} authorId={} hasParent={}", issueId, author.getId(), req.parentId() != null);
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        Comment comment = new Comment();
        comment.setIssue(issue);
        comment.setBody(req.body());
        comment.setAuthor(author);

        if (req.parentId() != null) {
            Comment parent = commentRepository.findById(req.parentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent comment not found"));
            if (!parent.getIssue().getId().equals(issueId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent comment does not belong to this issue");
            }
            comment.setParent(parent);
        }

        comment = commentRepository.save(comment);
        activityService.record(issue, author, ActivityEventType.COMMENT_ADDED, null, comment.getId().toString());
        log.debug("Comment created commentId={} issueId={} authorId={}", comment.getId(), issueId, author.getId());
        return comment;
    }

    public List<CommentDto> listThreaded(UUID issueId) {
        log.debug("Listing threaded comments issueId={}", issueId);
        List<Comment> all = commentRepository.findAllByIssueId(issueId);

        Map<UUID, List<Comment>> childrenByParent = all.stream()
            .filter(c -> c.getParent() != null)
            .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        List<CommentDto> threads = all.stream()
            .filter(c -> c.getParent() == null)
            .map(c -> buildDto(c, childrenByParent))
            .toList();
        log.debug("Listed threaded comments issueId={} topLevelCount={} totalCount={}",
                issueId, threads.size(), all.size());
        return threads;
    }

    private CommentDto buildDto(Comment comment, Map<UUID, List<Comment>> childrenByParent) {
        List<CommentDto> replies = childrenByParent.getOrDefault(comment.getId(), List.of())
            .stream()
            .map(c -> buildDto(c, childrenByParent))
            .toList();
        return CommentDto.fromWithReplies(comment, replies);
    }

    @Transactional
    public void delete(UUID issueId, UUID commentId, User requestingUser) {
        log.debug("Deleting comment commentId={} issueId={} requestingUserId={}", commentId, issueId, requestingUser.getId());
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        if (!comment.getIssue().getId().equals(issueId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this issue");
        }
        if (comment.getAuthor() != null && !comment.getAuthor().getId().equals(requestingUser.getId())) {
            log.warn("Unauthorized comment deletion attempt commentId={} authorId={} requestingUserId={}",
                    commentId, comment.getAuthor().getId(), requestingUser.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the comment author");
        }
        commentRepository.delete(comment);
        log.debug("Comment deleted commentId={} issueId={}", commentId, issueId);
    }
}
