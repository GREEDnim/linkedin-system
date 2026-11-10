package com.linkedin.searchservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.searchservice.event.UserCreatedEvent;
import com.linkedin.searchservice.event.UserUpdatedEvent;
import com.linkedin.searchservice.service.SearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the {@code users} index in step with user-service.
 *
 * <p>Recovery: the index is a projection with no database behind it, so a lost index
 * cannot rebuild itself from committed offsets. To rebuild, delete the index and restart
 * with a fresh consumer group (for example {@code --spring.kafka.consumer.group-id=search-service-rebuild-1}),
 * which replays the retained {@code user.created} / {@code user.updated} history from the
 * beginning. Kafka retention is therefore the real limit on how far back a rebuild can go.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final SearchService searchService;

    @KafkaListener(
            topics = "user.created",
            properties = {"spring.json.value.default.type=com.linkedin.searchservice.event.UserCreatedEvent"})
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Indexing new user: {}", event == null ? null : event.userId());
        searchService.indexNewUser(event);
    }

    @KafkaListener(
            topics = "user.updated",
            properties = {"spring.json.value.default.type=com.linkedin.searchservice.event.UserUpdatedEvent"})
    public void onUserUpdated(UserUpdatedEvent event) {
        log.info("Re-indexing updated user: {}", event == null ? null : event.userId());
        searchService.indexUpdatedUser(event);
    }
}
