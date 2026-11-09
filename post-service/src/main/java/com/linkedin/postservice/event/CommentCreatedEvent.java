package com.linkedin.postservice.event;

import java.time.LocalDateTime;

import com.linkedin.postservice.entity.Comment;

/**
 * Published when a comment is created. notification-service turns it into
 * "X commented on your post" for the post author.
 * Emitted with spring.json.add.type.headers: false, so the JSON body is the contract.
 */
public record CommentCreatedEvent(
        String commentId,
        String postId,
        String authorId,
        LocalDateTime createdAt) {

    public static CommentCreatedEvent from(Comment comment) {
        return new CommentCreatedEvent(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getCreatedAt());
    }
}
