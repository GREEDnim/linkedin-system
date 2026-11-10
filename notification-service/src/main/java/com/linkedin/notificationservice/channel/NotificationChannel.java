package com.linkedin.notificationservice.channel;

import com.linkedin.notificationservice.model.Notification;

/**
 * Delivery seam.
 *
 * <p>Right now the only implementation writes to the console. Persisting
 * notifications (a notifications table plus GET /notifications) and sending
 * email or push each become one more implementation, with no change to routing or
 * to the consumers - which is the only reason the interface exists this early.
 */
public interface NotificationChannel {

    void deliver(Notification notification);
}
