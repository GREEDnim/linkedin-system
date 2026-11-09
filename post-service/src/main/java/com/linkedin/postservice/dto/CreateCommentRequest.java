package com.linkedin.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/posts/{postId}/comments.
 * Comments are text only, so a JSON body is enough here (unlike a post, which
 * carries an optional image upload and is therefore multipart).
 */
public record CreateCommentRequest(

        @NotBlank(message = "Comment content must not be empty")
        @Size(max = 1000, message = "Comment content must not exceed 1000 characters")
        String content) {
}
