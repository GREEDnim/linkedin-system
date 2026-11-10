package com.linkedin.searchservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.searchservice.event.PostCreatedEvent;
import com.linkedin.searchservice.event.PostDeletedEvent;
import com.linkedin.searchservice.service.SearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the {@code posts} index in step with post-service.
 *
 * <p>Recovery is the same as for people: the index is a projection with no database behind
 * it, so rebuilding means deleting the index and replaying the topics under a fresh consumer
 * group. See {@link UserEventConsumer} for the procedure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventConsumer {

    private final SearchService searchService;

    @KafkaListener(
            topics = "post.created",
            properties = {"spring.json.value.default.type=com.linkedin.searchservice.event.PostCreatedEvent"})
    public void onPostCreated(PostCreatedEvent event) {
        log.info("Indexing post: {}", event == null ? null : event.postId());
        searchService.indexPost(event);
    }

    @KafkaListener(
            topics = "post.deleted",
            properties = {"spring.json.value.default.type=com.linkedin.searchservice.event.PostDeletedEvent"})
    public void onPostDeleted(PostDeletedEvent event) {
        log.info("De-indexing post: {}", event == null ? null : event.postId());
        searchService.deletePost(event);
    }
}
