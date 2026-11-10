package com.linkedin.postservice.event;

import java.time.LocalDateTime;

import com.linkedin.postservice.entity.Post;

/**
 * Published when a post is created. Consumed by feed-service (fan-out to the author's
 * connections) and search-service (indexing).
 * Emitted with spring.json.add.type.headers: false, so the JSON body is the contract.
 *
 * <p>Carries {@code content} because a search projection cannot be built without it, and
 * because the alternative - having search-service call back for every post - would couple
 * the two services at runtime. {@code imageUrl} is deliberately absent: display assets are
 * not indexed, the client reads them from the post endpoint.
 */
public record PostCreatedEvent(
        String postId,
        String authorId,
        String content,
        LocalDateTime createdAt) {

    public static PostCreatedEvent from(Post post) {
        return new PostCreatedEvent(
                post.getId(),
                post.getAuthorId(),
                post.getContent(),
                post.getCreatedAt());
    }
}
