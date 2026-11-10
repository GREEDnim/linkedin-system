package com.linkedin.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.notificationservice.event.CommentCreatedEvent;
import com.linkedin.notificationservice.event.PostLikedEvent;
import com.linkedin.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Engagement notices on a post: likes and comments, both addressed to the post author.
 *
 * <p>post.unliked and comment.deleted are deliberately NOT consumed yet. They exist to
 * withdraw a notification, and withdrawal needs somewhere to withdraw from. Once the
 * persisted channel lands, they become two one-line handlers that delete a row - the
 * event payloads were already shaped for it (same recipient, same actor, same subject).
 *
 * <p>post.created / post.deleted are also not consumed: a "your connection posted"
 * notice needs the connection graph, which this service does not project.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EngagementEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "post.liked",
            properties = {"spring.json.value.default.type=com.linkedin.notificationservice.event.PostLikedEvent"})
    public void onPostLiked(PostLikedEvent event) {

        log.info("Received post.liked: {}", event);
        notificationService.postLiked(event);
    }

    @KafkaListener(topics = "comment.created",
            properties = {"spring.json.value.default.type=com.linkedin.notificationservice.event.CommentCreatedEvent"})
    public void onCommentCreated(CommentCreatedEvent event) {

        log.info("Received comment.created: {}", event);
        notificationService.postCommented(event);
    }
}
