package com.linkedin.postservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.postservice.event.UserDeletedEvent;
import com.linkedin.postservice.service.PostService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes a deleted account's content.
 *
 * <p>This is the cascade that makes account deletion meaningful: without it, the posts of a
 * deleted user stay visible, stay searchable, and stay in other people's feeds pointing at an
 * author who no longer exists. Each removed post publishes post.deleted as usual, so
 * feed-service and search-service clean up through the paths they already have rather than
 * needing to know about accounts at all.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final PostService postService;

    @KafkaListener(topics = "user.deleted",
            properties = {"spring.json.value.default.type=com.linkedin.postservice.event.UserDeletedEvent"})
    public void onUserDeleted(UserDeletedEvent event) {

        log.info("Received user.deleted: {}", event);
        postService.deletePostsByAuthor(event.userId());
    }
}
