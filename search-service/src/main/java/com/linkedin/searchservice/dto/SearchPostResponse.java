package com.linkedin.searchservice.dto;

import com.linkedin.searchservice.document.PostDocument;

/**
 * One row of a post search result.
 *
 * <p>Just the identity and the text. No author name, no image, no counters: this index does
 * not hold them, and inventing them here would mean either denormalising user data into
 * every post document or calling user-service per hit. The client hydrates the card from the
 * post and profile endpoints - the same pointer-not-copy rule as people search.
 */
public record SearchPostResponse(
        String postId,
        String authorId,
        String content) {

    public static SearchPostResponse from(PostDocument document) {
        return new SearchPostResponse(
                document.getPostId(),
                document.getAuthorId(),
                document.getContent());
    }
}
