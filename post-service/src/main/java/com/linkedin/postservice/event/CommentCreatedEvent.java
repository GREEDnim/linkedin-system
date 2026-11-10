package com.linkedin.postservice.event;

import java.time.LocalDateTime;

import com.linkedin.postservice.entity.Comment;
import com.linkedin.postservice.entity.Post;

/**
 * Published when a comment is created.
 *
 * <p>Carries the post author as well as the commenter: notification-service delivers
 * to the post owner and names the commenter as the actor. The previous authorId was
 * the commenter, which left the consumer with nobody to notify.
 *
 * <p>Emitted with spring.json.add.type.headers: false, so the JSON body is the
 * contract.
 */
public record CommentCreatedEvent(
        String commentId,
        String postId,
        String postAuthorId,
        String commenterId,
        LocalDateTime createdAt) {

    public static CommentCreatedEvent from(Comment comment, Post post) {
        return new CommentCreatedEvent(
                comment.getId(),
                comment.getPostId(),
                post.getAuthorId(),
                comment.getAuthorId(),
                comment.getCreatedAt());
    }
}
