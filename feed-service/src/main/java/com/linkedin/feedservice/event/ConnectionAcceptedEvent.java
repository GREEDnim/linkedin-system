package com.linkedin.feedservice.event;

/**
 * Consumed form of user-service's connection.accepted event:
 * <pre>{"connectionId":"...","requesterId":"...","receiverId":"..."}</pre>
 *
 * <p>The JSON body is the contract: user-service stamps no __TypeId__ headers,
 * so these components must match the producer's record fields exactly.
 *
 * <p>connectionId is not used by the fan-out but is mirrored here so the
 * consumer's view of the producer's contract stays complete.
 */
public record ConnectionAcceptedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {
}
