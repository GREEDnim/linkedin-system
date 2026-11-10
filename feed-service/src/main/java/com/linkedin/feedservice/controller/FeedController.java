package com.linkedin.feedservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkedin.feedservice.dto.FeedResponse;
import com.linkedin.feedservice.exception.ApiException;
import com.linkedin.feedservice.service.FeedService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feed endpoints.
 *
 * <p>The caller is identified by the X-User-Id header, which the api-gateway is
 * expected to set after validating the JWT. A feed is personal: you may only
 * read your own (the same requireSelf rule as user-service).
 *
 * <p>Responses carry post IDs only. Hydrating them into actual posts is
 * post-service's job — this service decides order, nothing more.
 */
@RestController
@RequestMapping("/api/v1/feed")
@Slf4j
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /**
     * GET /api/v1/feed/{userId}?page=0&size=20 — the caller's feed, newest
     * first, paginated. page is 0-based, size is capped at 100 to stop anyone
     * from asking for the entire feed in one request and defeating pagination.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<FeedResponse> getFeed(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        requireSelf(userId, requestingUserId);
        log.info("Get feed of {} requested by {}", userId, requestingUserId);

        return ResponseEntity.ok(feedService.getFeed(userId, page, size));
    }

    /**
     * DELETE /api/v1/feed/{userId} — clears the caller's feed (DEL of the whole
     * key). 204 No Content: there is nothing left to return. A testing
     * convenience so a client can reset between runs without redis-cli.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearFeed(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        requireSelf(userId, requestingUserId);
        log.info("Clear feed of {} requested by {}", userId, requestingUserId);

        feedService.clearFeed(userId);
        return ResponseEntity.noContent().build();
    }

    /** A feed is personal: the path user must be the verified caller. */
    private void requireSelf(String userId, String requestingUserId) {
        if (!userId.equals(requestingUserId)) {
            throw ApiException.forbidden("You may only read your own feed");
        }
    }
}
