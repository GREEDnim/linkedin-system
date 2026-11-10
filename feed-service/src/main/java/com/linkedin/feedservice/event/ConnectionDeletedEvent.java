package com.linkedin.feedservice.event;

/**
 * Consumed form of user-service's connection.deleted event:
 * <pre>{"connectionId":"...","requesterId":"...","receiverId":"..."}</pre>
 *
 * <p>Same shape as connection.accepted — only the meaning differs: this pair is
 * no longer connected, so feed-service must stop fanning their posts to each other.
 */
public record ConnectionDeletedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {
}
