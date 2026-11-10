package com.linkedin.searchservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkedin.searchservice.dto.SearchPostResponse;
import com.linkedin.searchservice.dto.SearchResponse;
import com.linkedin.searchservice.dto.SearchUserResponse;
import com.linkedin.searchservice.service.SearchService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Read side of search. The write side is Kafka: nothing writes to an index over HTTP.
 *
 * <p>No self-check here, unlike the feed and profile endpoints: search is not scoped to a
 * caller, anyone authenticated may search for anyone or anything. {@code X-User-Id} is still
 * required so the endpoint cannot be reached without the gateway having authenticated the
 * request - it is an authentication gate, not an authorisation rule.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Searches people by name, headline, skills and location.
     *
     * <pre>GET /api/v1/search/users?q=ada&amp;page=0&amp;size=20</pre>
     */
    @GetMapping("/users")
    public ResponseEntity<SearchResponse<SearchUserResponse>> searchUsers(
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam("q")
            @NotBlank(message = "Search query must not be blank")
            @Size(max = 100, message = "Search query must not exceed 100 characters")
            String query,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 50, message = "size must not exceed 50")
            int size) {

        return ResponseEntity.ok(searchService.searchUsers(query, page, size));
    }

    /**
     * Searches post content.
     *
     * <pre>GET /api/v1/search/posts?q=distributed&amp;page=0&amp;size=20</pre>
     */
    @GetMapping("/posts")
    public ResponseEntity<SearchResponse<SearchPostResponse>> searchPosts(
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam("q")
            @NotBlank(message = "Search query must not be blank")
            @Size(max = 100, message = "Search query must not exceed 100 characters")
            String query,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 50, message = "size must not exceed 50")
            int size) {

        return ResponseEntity.ok(searchService.searchPosts(query, page, size));
    }
}
