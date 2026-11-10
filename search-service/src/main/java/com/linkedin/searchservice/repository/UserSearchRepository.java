package com.linkedin.searchservice.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.linkedin.searchservice.document.UserDocument;

/**
 * Spring Data repository for the {@code users} index.
 *
 * <p>The inherited CRUD methods are available but the service does not use them: documents
 * are written through {@link UserSearchRepositoryCustom#upsertPartial} so that an event
 * only touches the fields it actually carries. Add the {@code ...Custom} fragment and this
 * stays the single repository bean the rest of the code injects.
 */
public interface UserSearchRepository
        extends ElasticsearchRepository<UserDocument, String>, UserSearchRepositoryCustom {
}
