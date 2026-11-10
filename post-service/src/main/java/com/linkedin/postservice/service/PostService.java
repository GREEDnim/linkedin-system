package com.linkedin.postservice.service;

import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.linkedin.postservice.dto.PostResponse;
import com.linkedin.postservice.entity.Post;
import com.linkedin.postservice.event.PostCreatedEvent;
import com.linkedin.postservice.event.PostDeletedEvent;
import com.linkedin.postservice.exception.ApiException;
import com.linkedin.postservice.repository.CommentRepository;
import com.linkedin.postservice.repository.LikeRepository;
import com.linkedin.postservice.repository.PostRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Post business rules. Reads are marked readOnly so Hibernate skips dirty checking
 * and the session stays open while entities are mapped to DTOs (open-in-view is off).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private static final String POST_CREATED_TOPIC = "post.created";
    private static final String POST_DELETED_TOPIC = "post.deleted";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final S3Service s3Service;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Creates a post owned by {@code authorId}, taken from the verified caller
     * header — never from the request body.
     *
     * <p>The image arrives as a file, not as a URL: the bytes are uploaded here and
     * the URL is produced from the bucket. A client therefore can never point a post
     * at an arbitrary external host.
     */
    @Transactional
    public PostResponse createPost(String authorId, String content, MultipartFile image) {

        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isEmpty()) {
            // Defence in depth: the @NotBlank on the controller already covers HTTP.
            throw ApiException.badRequest("Post content must not be empty");
        }

        // Upload first: if S3 fails the request fails before anything is persisted.
        // Doing it after the insert would leave a row (and an event) pointing nowhere.
        S3Service.StoredObject uploadedImage = uploadImage(authorId, image);

        Post post = Post.builder()
                .authorId(authorId)
                .content(trimmedContent)
                .imageUrl(uploadedImage == null ? null : uploadedImage.url())
                .imageKey(uploadedImage == null ? null : uploadedImage.key())
                .build();

        Post saved = postRepository.saveAndFlush(post);

        // saveAndFlush, not save: @CreationTimestamp is only populated on flush,
        // and the event (and the response) must carry the real createdAt.
        kafkaTemplate.send(POST_CREATED_TOPIC, saved.getId(), PostCreatedEvent.from(saved));
        log.info("Post created: {} by {} (with image: {})",
                saved.getId(), authorId, uploadedImage != null);

        return PostResponse.from(saved);
    }

    /**
     * Deletes a post. Only its author may do so.
     *
     * <p>Comments and likes are children of the post, so they are removed in the
     * same transaction — otherwise they stay behind as rows nothing can reach
     * (and they would keep occupying the unique (post, user) key on likes).
     */
    @Transactional
    public void deletePost(String postId, String requestingUserId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found: " + postId));

        if (!post.getAuthorId().equals(requestingUserId)) {
            throw ApiException.forbidden("You may only delete your own posts");
        }

        int removedComments = commentRepository.deleteAllByPostId(postId);
        likeRepository.deleteAllByPostId(postId);

        postRepository.delete(post);

        // After the row is gone, and best-effort: the media is worthless without the post, but
        // a storage hiccup must not resurrect the post the author asked to remove.
        s3Service.deleteObject(post.getImageKey());

        kafkaTemplate.send(POST_DELETED_TOPIC, postId, PostDeletedEvent.from(post));
        log.info("Post {} deleted by {} ({} comments removed)", postId, requestingUserId, removedComments);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(String postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found: " + postId));

        return PostResponse.from(post);
    }

    /**
     * Removes every post by an account that no longer exists.
     *
     * <p>One transaction on purpose: an account deletion is rare, and a partial cascade would
     * leave content half-removed with no way to resume. Each post publishes post.deleted, so
     * feed-service and search-service clean up through the paths they already own instead of
     * needing to know what an account is.
     *
     * <p>At a scale where one account owns thousands of posts this belongs in a batch job,
     * since the whole set is held in a single transaction.
     */
    @Transactional
    public void deletePostsByAuthor(String authorId) {

        List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(authorId);

        for (Post post : posts) {
            commentRepository.deleteAllByPostId(post.getId());
            likeRepository.deleteAllByPostId(post.getId());
            postRepository.delete(post);
            s3Service.deleteObject(post.getImageKey());
            kafkaTemplate.send(POST_DELETED_TOPIC, post.getId(), PostDeletedEvent.from(post));
        }

        log.info("Removed {} posts for deleted account {}", posts.size(), authorId);
    }

    /** A profile's post list. Unknown authors simply return an empty list. */
    @Transactional(readOnly = true)
    public List<PostResponse> getPostsByAuthor(String authorId) {
        return postRepository.findByAuthorIdOrderByCreatedAtDesc(authorId).stream()
                .map(PostResponse::from)
                .toList();
    }

    /**
     * Stores the post image under posts/{authorId}/ and returns its public URL,
     * or null when no image was supplied. S3Service turns failures into 400/502.
     */
    private S3Service.StoredObject uploadImage(String authorId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        return s3Service.uploadFile(image, "posts/" + authorId + "/image");
    }
}
