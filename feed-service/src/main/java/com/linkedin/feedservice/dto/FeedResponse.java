package com.linkedin.feedservice.dto;

import java.util.List;

/**
 * One page of a user's feed.
 *
 * <p>Deliberately carries post IDs, not posts: this service knows what order
 * the posts should appear in, and nothing about their content. The client (or
 * the gateway) hydrates the IDs through post-service.
 */
public record FeedResponse(
        String userId,
        List<String> postIds,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
