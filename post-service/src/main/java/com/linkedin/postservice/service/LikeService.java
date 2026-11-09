package com.linkedin.postservice.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkedin.postservice.dto.PostLikeResponse;
import com.linkedin.postservice.entity.Like;
import com.linkedin.postservice.entity.Post;
import com.linkedin.postservice.event.PostLikedEvent;
import com.linkedin.postservice.event.PostUnlikedEvent;
import com.linkedin.postservice.exception.ApiException;
import com.linkedin.postservice.repository.LikeRepository;
import com.linkedin.postservice.repository.PostRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Like / unlike a post.
 *
 * <p>Both operations are idempotent on purpose: "like" twice is a no-op rather than
 * an error, because a retried request or a double click must not inflate
 * Post.likeCount. The unique (postId, authorId) constraint is the real guard — this
 * service-level check only avoids the common case.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LikeService {

    private static final String POST_LIKED_TOPIC = "post.liked";
    private static final String POST_UNLIKED_TOPIC = "post.unliked";

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public PostLikeResponse likePost(String postId, String authorId) {

        if (!postRepository.existsById(postId)) {
            throw ApiException.notFound("Post not found: " + postId);
        }

        if (likeRepository.existsByPostIdAndAuthorId(postId, authorId)) {
            log.debug("Post {} already liked by {} — no-op", postId, authorId);
            return currentState(postId, authorId);
        }

        likeRepository.saveAndFlush(Like.builder()
                .postId(postId)
                .authorId(authorId)
                .build());

        // Atomic +1 in SQL, same reasoning as commentCount: two people liking at
        // once must not overwrite each other's increment.
        postRepository.incrementLikeCount(postId);

        kafkaTemplate.send(POST_LIKED_TOPIC, postId, new PostLikedEvent(postId, authorId));
        log.info("Post {} liked by {}", postId, authorId);

        return currentState(postId, authorId);
    }

    @Transactional
    public PostLikeResponse unlikePost(String postId, String authorId) {

        if (!postRepository.existsById(postId)) {
            throw ApiException.notFound("Post not found: " + postId);
        }

        int removed = likeRepository.deleteLike(postId, authorId);
        if (removed > 0) {
            postRepository.decrementLikeCount(postId);
            kafkaTemplate.send(POST_UNLIKED_TOPIC, postId, new PostUnlikedEvent(postId, authorId));
            log.info("Post {} unliked by {}", postId, authorId);
        }
        // Removing a like that was never there is a no-op: DELETE is idempotent,
        // so no counter change and no event.

        return currentState(postId, authorId);
    }

    /** Fresh counter + this author's like state, read inside the transaction. */
    private PostLikeResponse currentState(String postId, String authorId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found: " + postId));

        return new PostLikeResponse(
                postId,
                post.getLikeCount(),
                likeRepository.existsByPostIdAndAuthorId(postId, authorId));
    }
}
