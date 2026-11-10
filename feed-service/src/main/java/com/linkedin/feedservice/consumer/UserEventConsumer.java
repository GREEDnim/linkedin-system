package com.linkedin.feedservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.feedservice.event.UserDeletedEvent;
import com.linkedin.feedservice.service.FeedService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Forgets a deleted account's feed and its place in the connection graph.
 *
 * <p>Three things have to go. The user's own feed, because it is personal data with no owner
 * left. Their connections set, for the same reason. And their id inside everybody else's
 * connections set, because that is what fan-out reads - leave it and every future post would
 * keep pushing into a feed nobody can log in to see.
 *
 * <p>Posting by the deleted account stops through post-service, which removes the posts and
 * publishes post.deleted; the existing un-fan-out path then clears them from feeds.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final FeedService feedService;

    @KafkaListener(topics = "user.deleted",
            properties = {"spring.json.value.default.type=com.linkedin.feedservice.event.UserDeletedEvent"})
    public void onUserDeleted(UserDeletedEvent event) {

        log.info("Received user.deleted: {}", event);
        feedService.removeUser(event.userId());
    }
}
