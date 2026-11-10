package com.linkedin.searchservice.repository;

import java.util.Map;

import org.springframework.data.domain.Page;

import com.linkedin.searchservice.document.UserDocument;

/**
 * The two operations a plain {@code ElasticsearchRepository} cannot express, wired in as a
 * Spring Data fragment so callers still see one repository bean.
 */
public interface UserSearchRepositoryCustom {

    /**
     * Writes only the given fields, creating the document if it is missing.
     *
     * <p>{@code save()} on the repository would replace the whole document. That matters
     * here because {@code user.created} and {@code user.updated} are separate topics
     * polled independently, so their order is not guaranteed: a full replace from a stale
     * create event would wipe the headline, location and skills an update had already
     * written. A partial document cannot, because it never mentions those fields.
     */
    void upsertPartial(String userId, Map<String, Object> fields);

    /** Multi-field people search, newest-relevance first, one page at a time. */
    Page<UserDocument> searchPeople(String query, int page, int size);
}
