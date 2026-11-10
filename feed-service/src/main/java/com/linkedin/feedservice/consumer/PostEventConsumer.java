package com.linkedin.feedservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.feedservice.event.PostCreatedEvent;
import com.linkedin.feedservice.event.PostDeletedEvent;
import com.linkedin.feedservice.service.FeedService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Push-model ingestion (fan-out on write): every post.created event is written
 * into every feed that should show it — the author's own plus all of their
 * connections, per the graph ConnectionEventConsumer maintains — so reading a
 * feed stays a single Redis slice.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PostEventConsumer {

    private final FeedService feedService;

    /**
     * The per-listener property tells the JSON deserializer which type to build:
     * the events carry no type headers, so this default is the only hint.
     */
    @KafkaListener(topics = "post.created",
            properties = {"spring.json.value.default.type=com.linkedin.feedservice.event.PostCreatedEvent"})
    public void onPostCreated(PostCreatedEvent event) {

        log.info("Ingesting post.created: {}", event);
        feedService.fanOutPost(event.authorId(), event.postId(), event.createdAt());
    }

    /**
     * A deleted post must leave the feeds it reached, otherwise the feed keeps
     * handing out an ID that post-service can no longer hydrate (the client would
     * get a 404 when it tries to load the post).
     */
    @KafkaListener(topics = "post.deleted",
            properties = {"spring.json.value.default.type=com.linkedin.feedservice.event.PostDeletedEvent"})
    public void onPostDeleted(PostDeletedEvent event) {

        log.info("Ingesting post.deleted: {}", event);
        feedService.removePostFromFeeds(event.authorId(), event.postId());
    }
}
