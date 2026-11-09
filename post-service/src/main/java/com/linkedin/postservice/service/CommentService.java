package com.linkedin.postservice.service;

import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkedin.postservice.dto.CommentResponse;
import com.linkedin.postservice.dto.CreateCommentRequest;
import com.linkedin.postservice.entity.Comment;
import com.linkedin.postservice.event.CommentCreatedEvent;
import com.linkedin.postservice.event.CommentDeletedEvent;
import com.linkedin.postservice.exception.ApiException;
import com.linkedin.postservice.repository.CommentRepository;
import com.linkedin.postservice.repository.PostRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comment business rules, including the denormalised Post.commentCount kept in
 * sync with atomic SQL updates.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentService {

    private static final String COMMENT_CREATED_TOPIC = "comment.created";
    private static final String COMMENT_DELETED_TOPIC = "comment.deleted";

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Adds a comment to an existing post. The author is the verified caller. */
    @Transactional
    public CommentResponse createComment(String postId, String authorId, CreateCommentRequest request) {

        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            // Defence in depth: the @NotBlank on the DTO already covers HTTP.
            throw ApiException.badRequest("Comment content must not be empty");
        }
        if (!postRepository.existsById(postId)) {
            throw ApiException.notFound("Post not found: " + postId);
        }

        Comment saved = commentRepository.saveAndFlush(Comment.builder()
                .postId(postId)
                .authorId(authorId)
                .content(content)
                .build());

        // Atomic +1 in SQL: loading the post and writing count+1 would lose
        // updates when two people comment at the same moment.
        postRepository.incrementCommentCount(postId);

        kafkaTemplate.send(COMMENT_CREATED_TOPIC, saved.getId(), CommentCreatedEvent.from(saved));
        log.info("Comment {} created on post {} by {}", saved.getId(), postId, authorId);

        return CommentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(String postId) {

        if (!postRepository.existsById(postId)) {
            throw ApiException.notFound("Post not found: " + postId);
        }

        return commentRepository.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    /**
     * Deletes a comment. Only its author may do so, and the comment must actually
     * belong to the post named in the URL — otherwise the path would be lying.
     */
    @Transactional
    public void deleteComment(String postId, String commentId, String requestingUserId) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("Comment not found: " + commentId));

        if (!comment.getPostId().equals(postId)) {
            throw ApiException.notFound("Comment not found: " + commentId);
        }
        if (!comment.getAuthorId().equals(requestingUserId)) {
            throw ApiException.forbidden("You may only delete your own comments");
        }

        commentRepository.delete(comment);
        postRepository.decrementCommentCount(postId);

        kafkaTemplate.send(COMMENT_DELETED_TOPIC, commentId, CommentDeletedEvent.from(comment));
        log.info("Comment {} deleted from post {} by {}", commentId, postId, requestingUserId);
    }
}
