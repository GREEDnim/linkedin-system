package com.linkedin.searchservice.repository;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchPage;

import com.linkedin.searchservice.document.PostDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Spring Data fragment implementation (the {@code ...Impl} naming is what wires it in). */
@RequiredArgsConstructor
@Slf4j
public class PostSearchRepositoryCustomImpl implements PostSearchRepositoryCustom {

    private static final String CONTENT_FIELD = "content";

    private final ElasticsearchOperations operations;
    private final PartialIndexWriter partialIndexWriter;

    @Override
    public void upsertPartial(String postId, Map<String, Object> fields) {
        partialIndexWriter.upsert(PostDocument.class, postId, fields);
    }

    @Override
    public Page<PostDocument> searchPosts(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        NativeQuery nativeQuery = NativeQuery.builder()
                // Only the content is analysed. fuzziness AUTO tolerates typos without
                // letting a one-letter query match everything.
                .withQuery(q -> q.match(m -> m
                        .field(CONTENT_FIELD)
                        .query(query)
                        .fuzziness("AUTO")))
                .withPageable(pageable)
                .build();

        SearchHits<PostDocument> hits = operations.search(nativeQuery, PostDocument.class);

        // SearchPage<Document> is a Page<SearchHit<Document>>; flatten to plain documents.
        SearchPage<PostDocument> searchPage = SearchHitSupport.searchPageFor(hits, pageable);
        return searchPage.map(SearchHit::getContent);
    }
}
