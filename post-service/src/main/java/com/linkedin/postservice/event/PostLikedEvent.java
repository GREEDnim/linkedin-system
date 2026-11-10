package com.linkedin.postservice.event;

import com.linkedin.postservice.entity.Post;

/**
 * Published when a post is liked.
 *
 * <p>Carries two ids because "who liked" and "whose post was liked" are different
 * people: notification-service addresses the notice to the author and names the
 * liker as the actor. The previous single authorId was the liker, which left the
 * consumer with no recipient to deliver to.
 *
 * <p>Emitted with spring.json.add.type.headers: false, so the JSON body is the
 * contract.
 */
public record PostLikedEvent(
        String postId,
        String postAuthorId,
        String likedBy) {

    public static PostLikedEvent of(Post post, String likedBy) {
        return new PostLikedEvent(post.getId(), post.getAuthorId(), likedBy);
    }
}
