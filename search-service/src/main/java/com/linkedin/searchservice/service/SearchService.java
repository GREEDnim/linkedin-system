package com.linkedin.searchservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.linkedin.searchservice.document.PostDocument;
import com.linkedin.searchservice.document.UserDocument;
import com.linkedin.searchservice.dto.SearchPostResponse;
import com.linkedin.searchservice.dto.SearchResponse;
import com.linkedin.searchservice.dto.SearchUserResponse;
import com.linkedin.searchservice.event.PostCreatedEvent;
import com.linkedin.searchservice.event.PostDeletedEvent;
import com.linkedin.searchservice.event.UserCreatedEvent;
import com.linkedin.searchservice.event.UserUpdatedEvent;
import com.linkedin.searchservice.exception.ApiException;
import com.linkedin.searchservice.repository.PostSearchRepository;
import com.linkedin.searchservice.repository.UserSearchRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the {@code users} and {@code posts} indexes from events and answers searches.
 *
 * <p>This service owns no database: both indexes are projections of user-service and
 * post-service, kept current by their events and rebuildable by replaying them. The
 * rebuild procedure is documented on
 * {@link com.linkedin.searchservice.consumer.UserEventConsumer}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    /** Elasticsearch refuses from+size beyond index.max_result_window (default 10k). */
    private static final int MAX_RESULT_WINDOW = 10_000;

    private final UserSearchRepository userSearchRepository;
    private final PostSearchRepository postSearchRepository;

    // ---------------------------------------------------------------- people index

    /**
     * Indexes a newly registered user.
     *
     * <p>Only the fields that exist at registration are written - name and email. The
     * profile edit that follows publishes {@code user.updated}, which adds headline,
     * location and skills without this handler having to guess at them.
     */
    public void indexNewUser(UserCreatedEvent event) {
        if (isInvalid(event == null ? null : event.userId(), "user.created")) {
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", event.userId());
        fields.put("firstName", event.firstName());
        fields.put("lastName", event.lastName());
        fields.put("email", normaliseEmail(event.email()));

        userSearchRepository.upsertPartial(event.userId(), fields);
    }

    /** Re-indexes a user after a profile edit. */
    public void indexUpdatedUser(UserUpdatedEvent event) {
        if (isInvalid(event == null ? null : event.userId(), "user.updated")) {
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", event.userId());
        fields.put("firstName", event.firstName());
        fields.put("lastName", event.lastName());
        fields.put("headline", event.headline());
        fields.put("location", event.location());
        fields.put("skills", event.skills() == null ? List.of() : event.skills());

        userSearchRepository.upsertPartial(event.userId(), fields);
    }

    public SearchResponse<SearchUserResponse> searchUsers(String rawQuery, int page, int size) {
        String query = validQuery(rawQuery, page, size);

        Page<UserDocument> results = userSearchRepository.searchPeople(query, page, size);
        List<SearchUserResponse> users = results.getContent().stream()
                .map(SearchUserResponse::from)
                .toList();

        log.debug("People search '{}' page {} size {} -> {} hits of {}",
                query, page, size, users.size(), results.getTotalElements());

        return envelope(query, users, page, size, results);
    }

    // ----------------------------------------------------------------- posts index

    /** Indexes a newly created post. */
    public void indexPost(PostCreatedEvent event) {
        if (isInvalid(event == null ? null : event.postId(), "post.created")) {
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("postId", event.postId());
        fields.put("authorId", event.authorId());
        fields.put("content", event.content());

        postSearchRepository.upsertPartial(event.postId(), fields);
    }

    /** Removes a deleted post, so it stops answering searches. */
    public void deletePost(PostDeletedEvent event) {
        if (isInvalid(event == null ? null : event.postId(), "post.deleted")) {
            return;
        }

        // No refresh policy: the delete becomes visible on the next ~1s refresh, like every
        // other write here. Forcing one per call was the same throughput trap as indexing.
        postSearchRepository.deleteById(event.postId());
    }

    public SearchResponse<SearchPostResponse> searchPosts(String rawQuery, int page, int size) {
        String query = validQuery(rawQuery, page, size);

        Page<PostDocument> results = postSearchRepository.searchPosts(query, page, size);
        List<SearchPostResponse> posts = results.getContent().stream()
                .map(SearchPostResponse::from)
                .toList();

        log.debug("Post search '{}' page {} size {} -> {} hits of {}",
                query, page, size, posts.size(), results.getTotalElements());

        return envelope(query, posts, page, size, results);
    }

    // -------------------------------------------------------------------- helpers

    /** Trims the query and rejects blank or over-deep paging before touching the cluster. */
    private String validQuery(String rawQuery, int page, int size) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            throw ApiException.badRequest("Search query must not be blank");
        }

        long from = (long) page * size;
        if (from + size > MAX_RESULT_WINDOW) {
            throw ApiException.badRequest(
                    "Result window too deep: page * size must not exceed " + MAX_RESULT_WINDOW
                            + "; refine the query instead of paging further");
        }

        return query;
    }

    private static <T> SearchResponse<T> envelope(
            String query, List<T> results, int page, int size, Page<?> source) {

        return new SearchResponse<>(
                query,
                results,
                page,
                size,
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext());
    }

    /** Keyword fields are case-sensitive, so normalise once on the way in. */
    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** An event without an id cannot be indexed, so skip it loudly instead of NPE. */
    private static boolean isInvalid(String id, String topic) {
        if (id == null || id.isBlank()) {
            log.warn("Skipping {} event without an id", topic);
            return true;
        }
        return false;
    }
}
