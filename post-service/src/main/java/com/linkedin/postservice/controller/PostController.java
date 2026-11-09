package com.linkedin.postservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.linkedin.postservice.dto.PostResponse;
import com.linkedin.postservice.service.PostService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Post endpoints.
 *
 * <p>The caller is identified by the X-User-Id header, which the api-gateway is
 * expected to set after validating the JWT. The author of a new post is always
 * taken from that header, never from the request body.
 *
 * <p>Naming note: POST /api/v1/posts IS the create operation — in REST, POST to a
 * collection means "create in this collection". The 201 status and the Location
 * header carry the intent, so no "/create" suffix is needed.
 */
@RestController
@RequestMapping("/api/v1/posts")
@Slf4j
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * Creates a post as multipart/form-data:
     * <ul>
     *   <li>{@code content} — required text</li>
     *   <li>{@code image} — optional file; the server uploads it to S3 and stores
     *       the resulting URL. The client never supplies a URL.</li>
     * </ul>
     * Returns 201 with a Location header pointing at the new resource.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam("content")
            @NotBlank(message = "Post content must not be empty")
            @Size(max = 3000, message = "Post content must not exceed 3000 characters")
            String content,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        PostResponse created = postService.createPost(requestingUserId, content, image);
        log.info("Post {} created by {}", created.id(), requestingUserId);

        return ResponseEntity
                .created(URI.create("/api/v1/posts/" + created.id()))
                .body(created);
    }

    /** Any authenticated user may read any post. */
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        log.info("Get post {} requested by {}", postId, requestingUserId);
        return ResponseEntity.ok(postService.getPost(postId));
    }

    /**
     * Deletes your own post (and its comments and likes). 204 No Content — there is
     * nothing left to return. Someone else's post returns 403, a missing one 404.
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        postService.deletePost(postId, requestingUserId);
        log.info("Post {} deleted by {}", postId, requestingUserId);

        return ResponseEntity.noContent().build();
    }

    /**
     * All posts of one author, newest first. Reading your own list means passing
     * your own id here; reading someone else's feeds their profile page.
     */
    @GetMapping("/user/{authorId}")
    public ResponseEntity<List<PostResponse>> getPostsByAuthor(
            @PathVariable String authorId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        log.info("Get posts of {} requested by {}", authorId, requestingUserId);
        return ResponseEntity.ok(postService.getPostsByAuthor(authorId));
    }
}
