package com.linkedin.postservice.event;

/**
 * Published when a post is liked. notification-service turns it into
 * "X liked your post" for the post author.
 * Emitted with spring.json.add.type.headers: false, so the JSON body is the contract.
 */
public record PostLikedEvent(
        String postId,
        String authorId) {
}
