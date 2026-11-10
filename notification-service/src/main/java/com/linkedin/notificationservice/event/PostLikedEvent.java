package com.linkedin.notificationservice.event;

/**
 * Mirror of post-service's post.liked payload.
 *
 * <p>postAuthorId is the recipient and likedBy the actor. Both are carried because
 * they are different people; an event with only the liker gave this consumer nobody
 * to address.
 */
public record PostLikedEvent(
        String postId,
        String postAuthorId,
        String likedBy) {
}
