package com.linkedin.notificationservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.linkedin.notificationservice.channel.NotificationChannel;
import com.linkedin.notificationservice.event.CommentCreatedEvent;
import com.linkedin.notificationservice.event.ConnectionAcceptedEvent;
import com.linkedin.notificationservice.event.ConnectionRequestedEvent;
import com.linkedin.notificationservice.event.PostLikedEvent;
import com.linkedin.notificationservice.event.UserCreatedEvent;
import com.linkedin.notificationservice.model.Notification;
import com.linkedin.notificationservice.model.NotificationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns domain events into notifications: decides <em>who</em> is told, then hands
 * the result to every delivery channel.
 *
 * <p>Recipient rules, all of them "the owner of the thing acted upon":
 * <ul>
 *   <li>connection.requested -&gt; the receiver (they are being asked)</li>
 *   <li>connection.accepted  -&gt; the requester (they were waiting)</li>
 *   <li>post.liked           -&gt; the post author</li>
 *   <li>comment.created      -&gt; the post author</li>
 *   <li>user.created         -&gt; the new user themselves</li>
 * </ul>
 *
 * <p>Known limitations of this slice, both removed by persistence rather than by
 * more routing:
 * <ul>
 *   <li>No store, so a Kafka redelivery can log the same notification twice. Once
 *       notifications are persisted, (recipientId, type, actorId, subjectId)
 *       becomes the natural dedupe key.</li>
 *   <li>Withdrawal events (post.unliked, comment.deleted, connection.deleted) are
 *       not consumed: there is nothing stored to delete yet, and logging a
 *       "withdrawn" notice for something never kept would be a lie.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    /**
     * Injected as a list so extra channels (persisted inbox, email, push) can be
     * added as beans without touching this class.
     */
    private final List<NotificationChannel> channels;

    public void connectionRequested(ConnectionRequestedEvent event) {
        notify(event.receiverId(), NotificationType.CONNECTION_REQUEST,
                event.requesterId(), event.connectionId());
    }

    public void connectionAccepted(ConnectionAcceptedEvent event) {
        notify(event.requesterId(), NotificationType.CONNECTION_ACCEPTED,
                event.receiverId(), event.connectionId());
    }

    public void postLiked(PostLikedEvent event) {
        notify(event.postAuthorId(), NotificationType.POST_LIKED,
                event.likedBy(), event.postId());
    }

    public void postCommented(CommentCreatedEvent event) {
        notify(event.postAuthorId(), NotificationType.POST_COMMENTED,
                event.commenterId(), event.postId());
    }

    public void welcome(UserCreatedEvent event) {
        notify(event.userId(), NotificationType.WELCOME, null, event.userId());
    }

    /**
     * Single delivery path: every notification type goes through here, so the
     * suppression rule below cannot be forgotten on a newly added type.
     */
    private void notify(String recipientId, NotificationType type, String actorId, String subjectId) {

        if (recipientId == null || recipientId.isBlank()) {
            log.warn("Skipping {} - no recipient in the event", type);
            return;
        }

        // Liking or commenting on your own post is possible; telling yourself
        // about it is noise.
        if (recipientId.equals(actorId)) {
            log.debug("Skipping {} - {} acted on their own content", type, recipientId);
            return;
        }

        Notification notification = Notification.of(recipientId, type, actorId, subjectId);
        channels.forEach(channel -> channel.deliver(notification));
    }
}
