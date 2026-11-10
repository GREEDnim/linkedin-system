package com.linkedin.notificationservice.event;

import java.time.LocalDateTime;

/**
 * Mirror of post-service's comment.created payload.
 *
 * <p>postAuthorId is the recipient, commenterId the actor. The timestamp is carried
 * through rather than recomputed, so a replayed event produces the same notification
 * time it would have produced originally.
 */
public record CommentCreatedEvent(
        String commentId,
        String postId,
        String postAuthorId,
        String commenterId,
        LocalDateTime createdAt) {
}
