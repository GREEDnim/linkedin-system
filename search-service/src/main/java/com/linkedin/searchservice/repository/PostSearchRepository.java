package com.linkedin.searchservice.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.linkedin.searchservice.document.PostDocument;

/** Spring Data repository for the {@code posts} index. */
public interface PostSearchRepository
        extends ElasticsearchRepository<PostDocument, String>, PostSearchRepositoryCustom {
}
