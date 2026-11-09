package com.linkedin.postservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkedin.postservice.dto.CommentResponse;
import com.linkedin.postservice.dto.CreateCommentRequest;
import com.linkedin.postservice.service.CommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comment endpoints, nested under their post because a comment has no meaning
 * without one: /api/v1/posts/{postId}/comments.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@Slf4j
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** Adds a comment as the caller (X-User-Id). 201 + Location of the comment. */
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @Valid @RequestBody CreateCommentRequest request) {

        CommentResponse created = commentService.createComment(postId, requestingUserId, request);
        log.info("Comment {} created on post {} by {}", created.id(), postId, requestingUserId);

        return ResponseEntity
                .created(URI.create("/api/v1/posts/" + postId + "/comments/" + created.id()))
                .body(created);
    }

    /** Any authenticated user may read the comments of any post. */
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        log.info("Get comments of post {} requested by {}", postId, requestingUserId);
        return ResponseEntity.ok(commentService.getComments(postId));
    }

    /** Deletes your own comment. 204 No Content — there is nothing left to return. */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String postId,
            @PathVariable String commentId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        commentService.deleteComment(postId, commentId, requestingUserId);
        return ResponseEntity.noContent().build();
    }
}
