package com.linkedin.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.linkedin.notificationservice.event.UserCreatedEvent;
import com.linkedin.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Welcome notice on registration.
 *
 * <p>user.updated is NOT consumed: a profile edit is not something anyone needs to be
 * told about. search-service consumes it instead, which is the right kind of consumer
 * for that event - one that maintains a projection rather than contacting a human.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "user.created",
            properties = {"spring.json.value.default.type=com.linkedin.notificationservice.event.UserCreatedEvent"})
    public void onUserCreated(UserCreatedEvent event) {

        log.info("Received user.created: {}", event);
        notificationService.welcome(event);
    }
}
