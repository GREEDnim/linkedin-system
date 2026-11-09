package com.linkedin.postservice.event;

import java.time.LocalDateTime;

import com.linkedin.postservice.entity.Post;

/**
 * Published when a post is created. Consumed by feed-service (fan-out to the
 * author's connections) and search-service (indexing).
 * Emitted with spring.json.add.type.headers: false, so the JSON body is the contract.
 */
public record PostCreatedEvent(
        String postId,
        String authorId,
        LocalDateTime createdAt) {

    public static PostCreatedEvent from(Post post) {
        return new PostCreatedEvent(
                post.getId(),
                post.getAuthorId(),
                post.getCreatedAt());
    }
}
