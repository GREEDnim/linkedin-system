package com.linkedin.postservice.dto;

import java.time.LocalDateTime;

import com.linkedin.postservice.entity.Post;

/**
 * Post as returned to clients. A record keeps it immutable; the static factory is
 * the only way to build one, so entities never leak out of the service layer.
 */
public record PostResponse(
        String id,
        String authorId,
        String content,
        String imageUrl,
        int likeCount,
        int commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                post.getContent(),
                post.getImageUrl(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
