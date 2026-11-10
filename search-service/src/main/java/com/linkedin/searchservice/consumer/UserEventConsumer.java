package com.linkedin.searchservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.searchservice.event.UserCreatedEvent;
import com.linkedin.searchservice.event.UserDeletedEvent;
import com.linkedin.searchservice.event.UserUpdatedEvent;
import com.linkedin.searchservice.service.SearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains the people index from the user lifecycle: index on create, re-index on update,
 * remove on delete.
 *
 * <p>The index is a projection: it can only forget someone if it is told. Without this listener
 * a deleted user stays findable by name, headline, location and skills indefinitely - searchable
 * personal data for an account that no longer exists.
 */
@Component
@Slf4j
@RequiredArgsConstructor
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

    @KafkaListener(topics = "user.deleted",
            properties = {"spring.json.value.default.type=com.linkedin.searchservice.event.UserDeletedEvent"})
    public void onUserDeleted(UserDeletedEvent event) {

        log.info("Received user.deleted: {}", event);
        searchService.removeUser(event.userId());
    }
}
