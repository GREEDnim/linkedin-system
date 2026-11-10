package com.linkedin.notificationservice.channel;

import org.springframework.stereotype.Component;

import com.linkedin.notificationservice.model.Notification;

import lombok.extern.slf4j.Slf4j;

/**
 * The placeholder channel: writes the notification to the application log.
 *
 * <p>Deliberately not fancier - the point of this slice is to prove the event
 * stream carries everything needed to address a notification. Delivery channels
 * (persisted inbox, email, push) arrive as sibling implementations.
 */
@Component
@Slf4j
public class LoggingNotificationChannel implements NotificationChannel {

    @Override
    public void deliver(Notification notification) {
        log.info("NOTIFY [{}] to={} actor={} subject={} at={}",
                notification.type(),
                notification.recipientId(),
                notification.actorId(),
                notification.subjectId(),
                notification.createdAt());
    }
}
