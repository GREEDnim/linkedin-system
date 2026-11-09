package com.linkedin.postservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkedin.postservice.dto.PostLikeResponse;
import com.linkedin.postservice.service.LikeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Like / unlike endpoints, nested under the post:
 * /api/v1/posts/{postId}/like is "the caller's like on this post".
 *
 * <p>PUT and DELETE rather than POST/GET: both are idempotent by definition, which
 * matches the semantics we want — liking twice leaves one like, unliking twice
 * leaves none, and a retried request is harmless.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/like")
@Slf4j
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /** Likes the post as the caller. 200 with the new like state and counter. */
    @PutMapping
    public ResponseEntity<PostLikeResponse> likePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        PostLikeResponse state = likeService.likePost(postId, requestingUserId);
        log.info("Like post {} by {} -> liked={}, likeCount={}",
                postId, requestingUserId, state.liked(), state.likeCount());

        return ResponseEntity.ok(state);
    }

    /** Removes the caller's like. 200 with the new state; safe to call twice. */
    @DeleteMapping
    public ResponseEntity<PostLikeResponse> unlikePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        PostLikeResponse state = likeService.unlikePost(postId, requestingUserId);
        log.info("Unlike post {} by {} -> liked={}, likeCount={}",
                postId, requestingUserId, state.liked(), state.likeCount());

        return ResponseEntity.ok(state);
    }
}
