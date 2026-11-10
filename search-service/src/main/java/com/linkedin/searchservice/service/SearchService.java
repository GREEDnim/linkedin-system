package com.linkedin.searchservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.linkedin.searchservice.document.UserDocument;
import com.linkedin.searchservice.dto.SearchResponse;
import com.linkedin.searchservice.dto.SearchUserResponse;
import com.linkedin.searchservice.event.UserCreatedEvent;
import com.linkedin.searchservice.event.UserUpdatedEvent;
import com.linkedin.searchservice.exception.ApiException;
import com.linkedin.searchservice.repository.UserSearchRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the {@code users} index from events and answers people searches.
 *
 * <p>This service owns no database: the index is a projection of user-service, kept
 * current by {@code user.created} / {@code user.updated}. It can therefore be rebuilt by
 * replaying those topics - the procedure is documented on
 * {@link com.linkedin.searchservice.consumer.UserEventConsumer}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    /** Elasticsearch refuses from+size beyond index.max_result_window (default 10k). */
    private static final int MAX_RESULT_WINDOW = 10_000;

    private final UserSearchRepository userSearchRepository;

    /**
     * Indexes a newly registered user.
     *
     * <p>Only the fields that exist at registration are written - name and email. The
     * profile edit that follows publishes {@code user.updated}, which adds headline,
     * location, skills and the photo url without this handler ever having to guess at them.
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

    /** Re-indexes a user after a profile edit or a profile photo change. */
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

        Page<UserDocument> results = userSearchRepository.searchPeople(query, page, size);

        List<SearchUserResponse> users = results.getContent().stream()
                .map(SearchUserResponse::from)
                .toList();

        log.debug("People search '{}' page {} size {} -> {} hits of {}",
                query, page, size, users.size(), results.getTotalElements());

        return new SearchResponse<>(
                query,
                users,
                page,
                size,
                results.getTotalElements(),
                results.getTotalPages(),
                results.hasNext());
    }

    /** Keyword fields are case-sensitive, so normalise once on the way in. */
    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** An event without a user id cannot be indexed, so skip it loudly instead of NPE. */
    private static boolean isInvalid(String userId, String topic) {
        if (userId == null || userId.isBlank()) {
            log.warn("Skipping {} event without a userId", topic);
            return true;
        }
        return false;
    }
}
