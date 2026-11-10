package com.linkedin.feedservice.event;

/**
 * Consumed form of post-service's post.deleted event:
 * <pre>{"postId":"...","authorId":"..."}</pre>
 *
 * <p>The JSON body is the contract (no __TypeId__ headers), so these components
 * must match the producer's record fields exactly.
 */
public record PostDeletedEvent(
        String postId,
        String authorId) {
}
