package com.linkedin.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.notificationservice.event.ConnectionAcceptedEvent;
import com.linkedin.notificationservice.event.ConnectionRequestedEvent;
import com.linkedin.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Connection lifecycle notices: "someone asked to connect" and "your invitation was
 * accepted".
 *
 * <p>connection.deleted (a disconnect) is deliberately NOT consumed. LinkedIn does
 * not tell you when someone removes you, and we chose the same silent behaviour for
 * rejection in user-service - surfacing a disconnect would be a product change, not a
 * missing consumer.
 *
 * <p>The per-listener property names the type to deserialize into: the events carry
 * no type headers, so this is the only hint the JSON deserializer gets.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectionEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "connection.requested",
            properties = {"spring.json.value.default.type=com.linkedin.notificationservice.event.ConnectionRequestedEvent"})
    public void onConnectionRequested(ConnectionRequestedEvent event) {

        log.info("Received connection.requested: {}", event);
        notificationService.connectionRequested(event);
    }

    @KafkaListener(topics = "connection.accepted",
            properties = {"spring.json.value.default.type=com.linkedin.notificationservice.event.ConnectionAcceptedEvent"})
    public void onConnectionAccepted(ConnectionAcceptedEvent event) {

        log.info("Received connection.accepted: {}", event);
        notificationService.connectionAccepted(event);
    }
}
