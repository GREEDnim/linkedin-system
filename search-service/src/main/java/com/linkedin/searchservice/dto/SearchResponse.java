package com.linkedin.searchservice.dto;

import java.util.List;

/**
 * Pagination envelope shared by every search endpoint, mirroring feed-service's
 * FeedResponse shape so clients can treat "a page of something" uniformly.
 *
 * @param query echoed back so a client can discard a response that no longer matches
 *              what the user has typed (search-as-you-type races)
 * @param totalElements exact below {@code index.max_result_window} (10k by default);
 *                      beyond that Elasticsearch reports a lower bound, which is why
 *                      deep paging is rejected at the service layer instead
 */
public record SearchResponse<T>(
        String query,
        List<T> results,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
