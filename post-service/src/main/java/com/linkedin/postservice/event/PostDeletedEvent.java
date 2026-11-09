package com.linkedin.postservice.event;

import com.linkedin.postservice.entity.Post;

/**
 * Published when a post is deleted, so downstream copies can drop it: feed-service
 * removes it from timelines, search-service de-indexes it, notification-service
 * cancels pending notifications about it.
 */
public record PostDeletedEvent(
        String postId,
        String authorId) {

    public static PostDeletedEvent from(Post post) {
        return new PostDeletedEvent(post.getId(), post.getAuthorId());
    }
}
