package com.linkedin.searchservice.repository;

import java.util.Map;

import org.springframework.data.domain.Page;

import com.linkedin.searchservice.document.PostDocument;

/** The two operations a plain ElasticsearchRepository cannot express, as a Spring Data fragment. */
public interface PostSearchRepositoryCustom {

    /** Writes only the given fields, creating the document if it is missing. */
    void upsertPartial(String postId, Map<String, Object> fields);

    /** Full-text search over post content, one page at a time. */
    Page<PostDocument> searchPosts(String query, int page, int size);
}
