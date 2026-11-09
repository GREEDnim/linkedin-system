package com.linkedin.postservice.event;

import com.linkedin.postservice.entity.Comment;

/**
 * Published when a comment is deleted, so downstream copies (notification feeds,
 * search index, counters) can drop it. Carries no content: the text is already gone.
 */
public record CommentDeletedEvent(
        String commentId,
        String postId,
        String authorId) {

    public static CommentDeletedEvent from(Comment comment) {
        return new CommentDeletedEvent(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId());
    }
}
