package com.linkedin.searchservice.repository;

import java.util.Map;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The one place that knows how to patch a document instead of replacing it.
 *
 * <p>Elasticsearch offers partial updates; Spring Data exposes them through
 * {@code ElasticsearchOperations.update(UpdateQuery, ...)} rather than through
 * {@code ElasticsearchRepository.save(...)}, which maps to the index API (a full replace).
 * Every projection in this service needs the patch form - an event only ever carries part of
 * the document - so the recipe lives here once instead of in each repository fragment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartialIndexWriter {

    private final ElasticsearchOperations operations;

    /**
     * Writes only the supplied fields, creating the document if it does not exist.
     *
     * <p>No refresh policy is set, so Elasticsearch's normal ~1s refresh makes the write
     * visible: search is near-real-time by design, and a client that must read its own
     * write belongs on the owning service, not on a projection. Forcing a refresh per
     * document (WAIT_UNTIL) was measurably worse - it capped indexing at about one
     * document per second, which turns a topic replay of a few thousand posts into hours.
     */
    public void upsert(Class<?> documentType, String id, Map<String, Object> fields) {
        UpdateQuery update = UpdateQuery.builder(id)
                .withDocument(Document.from(fields))
                .withDocAsUpsert(true)
                .build();

        operations.update(update, operations.getIndexCoordinatesFor(documentType));
        log.debug("Indexed {} {}", documentType.getSimpleName(), id);
    }
}
