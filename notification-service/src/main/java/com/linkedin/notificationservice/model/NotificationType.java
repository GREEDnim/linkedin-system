package com.linkedin.notificationservice.model;

/**
 * The kinds of thing this service tells a user about.
 *
 * <p>Kept as an enum rather than free text so routing stays exhaustive: a new
 * event becomes a compile-time decision about who receives it, not a string that
 * silently reaches no one.
 */
public enum NotificationType {

    /** Someone asked to connect with you. */
    CONNECTION_REQUEST,

    /** Someone you invited accepted. */
    CONNECTION_ACCEPTED,

    /** Someone liked your post. */
    POST_LIKED,

    /** Someone commented on your post. */
    POST_COMMENTED,

    /** A brand-new account - the one notice a user gets about themselves. */
    WELCOME
}
