package com.linkedin.notificationservice.model;

import java.time.LocalDateTime;

/**
 * One thing to tell one user about, independent of how it is delivered.
 *
 * @param recipientId the user who should see this - always the owner of the thing
 *                    acted upon, never the person who acted
 * @param type        what happened, which also decides the message template later
 * @param actorId     who caused it; null for notices with no actor (WELCOME)
 * @param subjectId   the thing acted upon (connectionId, postId, ...), so a client
 *                    can deep-link and a stored notification can be withdrawn later
 */
public record Notification(
        String recipientId,
        NotificationType type,
        String actorId,
        String subjectId,
        LocalDateTime createdAt) {

    public static Notification of(String recipientId, NotificationType type, String actorId, String subjectId) {
        return new Notification(recipientId, type, actorId, subjectId, LocalDateTime.now());
    }
}
