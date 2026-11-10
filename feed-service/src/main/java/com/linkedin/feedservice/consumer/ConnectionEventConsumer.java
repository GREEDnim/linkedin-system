package com.linkedin.feedservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.feedservice.event.ConnectionAcceptedEvent;
import com.linkedin.feedservice.event.ConnectionDeletedEvent;
import com.linkedin.feedservice.service.FeedService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains feed-service's local projection of the connection graph:
 * connections:{userId} sets in Redis, written from connection.accepted and
 * undone by connection.deleted.
 *
 * <p>connection.requested is deliberately NOT consumed — a pending request does
 * not make anyone's posts visible. Only an accepted connection is mutual.
 *
 * <p>Removing a connection stops <em>future</em> fan-out in both directions. Posts
 * already delivered stay in the other person's feed (delivery happened); LinkedIn
 * behaves the same way. Purging them would need the reverse index
 * (post:{postId} -> userIds) that fan-out deliberately does not keep.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectionEventConsumer {

    private final FeedService feedService;

    /**
     * The per-listener property tells the JSON deserializer which type to build:
     * the events carry no type headers, so this default is the only hint.
     */
    @KafkaListener(topics = "connection.accepted",
            properties = {"spring.json.value.default.type=com.linkedin.feedservice.event.ConnectionAcceptedEvent"})
    public void onConnectionAccepted(ConnectionAcceptedEvent event) {

        log.info("Ingesting connection.accepted: {}", event);
        feedService.addConnection(event.requesterId(), event.receiverId());
    }

    /** Disconnect: drop both directions from the graph. A later re-connect re-adds them. */
    @KafkaListener(topics = "connection.deleted",
            properties = {"spring.json.value.default.type=com.linkedin.feedservice.event.ConnectionDeletedEvent"})
    public void onConnectionDeleted(ConnectionDeletedEvent event) {

        log.info("Ingesting connection.deleted: {}", event);
        feedService.removeConnection(event.requesterId(), event.receiverId());
    }
}
