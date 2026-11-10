package com.linkedin.feedservice.event;

import java.time.LocalDateTime;

/**
 * Consumed form of post-service's post.created event:
 * <pre>{"postId":"...","authorId":"...","createdAt":"2026-11-09T22:42:09.008283"}</pre>
 *
 * <p>The JSON body is the contract: post-service stamps no __TypeId__ headers
 * (spring.json.add.type.headers: false), so these components must match the
 * producer's {@code PostCreatedEvent} fields exactly.
 *
 * <p>createdAt is the post's creation LocalDateTime as post-service's server
 * clock (no zone). This service treats it as UTC when scoring it into the feed
 * zset — ordering needs only consistent conversion; the absolute meaning of
 * the score matters only for future "everything newer than T" queries.
 */
public record PostCreatedEvent(
        String postId,
        String authorId,
        LocalDateTime createdAt) {
}
