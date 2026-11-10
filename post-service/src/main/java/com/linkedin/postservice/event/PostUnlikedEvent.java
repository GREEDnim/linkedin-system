package com.linkedin.postservice.event;

import com.linkedin.postservice.entity.Post;

/**
 * Published when a like is removed, so notification-service can withdraw the
 * "X liked your post" notice instead of leaving a stale one behind.
 *
 * <p>Mirrors PostLikedEvent field for field: a withdrawal can only be matched to
 * the notification it reverses if both events identify the same recipient and the
 * same actor.
 */
public record PostUnlikedEvent(
        String postId,
        String postAuthorId,
        String unlikedBy) {

    public static PostUnlikedEvent of(Post post, String unlikedBy) {
        return new PostUnlikedEvent(post.getId(), post.getAuthorId(), unlikedBy);
    }
}
