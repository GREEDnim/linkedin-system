package com.linkedin.postservice.dto;

import java.time.LocalDateTime;

import com.linkedin.postservice.entity.Comment;

/**
 * Comment as returned to clients.
 */
public record CommentResponse(
        String id,
        String postId,
        String authorId,
        String content,
        LocalDateTime createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}
