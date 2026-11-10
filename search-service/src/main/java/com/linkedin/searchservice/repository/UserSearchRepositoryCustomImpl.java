package com.linkedin.searchservice.repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import com.linkedin.searchservice.document.UserDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Data fragment implementation. Naming matters: Spring Data looks for
 * {@code <FragmentInterface>Impl}, so this class is what makes {@code UserSearchRepository}
 * satisfy {@link UserSearchRepositoryCustom}.
 */
@RequiredArgsConstructor
@Slf4j
public class UserSearchRepositoryCustomImpl implements UserSearchRepositoryCustom {

    /**
     * Fields searched for people, with their relevance weight.
     *
     * <p>{@code fullName} gets the highest boost because a query matching the whole name is
     * almost always the intended person; first and last name follow so a single-token query
     * still ranks well; headline, skills and location are supporting signals - matching
     * "London" should surface someone, but never outrank a name match. Email is not here: it
     * is a keyword and matched exactly by its own clause.
     */
    private static final List<String> SEARCH_FIELDS = List.of(
            "fullName^4",
            "firstName^3",
            "lastName^3",
            "headline^2",
            "skills^1.5",
            "location^1");

    private final ElasticsearchOperations operations;

    @Override
    public void upsertPartial(String userId, Map<String, Object> fields) {
        UpdateQuery update = UpdateQuery.builder(userId)
                .withDocument(Document.from(fields))
                .withDocAsUpsert(true)
                // WAIT_UNTIL makes the write visible to the very next search. That costs a
                // refresh per document - fine at this volume, and it makes the pipeline
                // deterministic instead of flaky.
                .withRefreshPolicy(RefreshPolicy.WAIT_UNTIL)
                .build();

        operations.update(update, operations.getIndexCoordinatesFor(UserDocument.class));
        log.debug("Indexed user {}", userId);
    }

    @Override
    public Page<UserDocument> searchPeople(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String lowerCaseQuery = query.toLowerCase(Locale.ROOT);

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        // Analysed text fields: fuzzy, boosted by weight.
                        .should(s -> s.multiMatch(m -> m
                                .query(query)
                                .fields(SEARCH_FIELDS)
                                // AUTO: tolerate typos without letting a one-letter query
                                // match everything.
                                .fuzziness("AUTO")))
                        // Email: exact match only, against the lower-cased keyword field.
                        .should(s -> s.term(t -> t
                                .field("email")
                                .value(lowerCaseQuery)))))
                .withPageable(pageable)
                .build();

        SearchHits<UserDocument> hits = operations.search(nativeQuery, UserDocument.class);

        // SearchPage<Document> is a Page<SearchHit<Document>>; flatten the hits away so
        // callers get plain documents and a normal Page (total, pages, hasNext).
        SearchPage<UserDocument> searchPage = SearchHitSupport.searchPageFor(hits, pageable);
        return searchPage.map(SearchHit::getContent);
    }
}
