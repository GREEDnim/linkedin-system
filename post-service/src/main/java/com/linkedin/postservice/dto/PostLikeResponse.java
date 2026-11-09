package com.linkedin.postservice.dto;

/**
 * Result of a like or unlike. Both operations return the same shape so the client
 * can update its UI (heart filled/unfilled + new counter) without a second request.
 */
public record PostLikeResponse(
        String postId,
        int likeCount,
        boolean liked) {
}
