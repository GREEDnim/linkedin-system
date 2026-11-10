package com.linkedin.userservice.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.linkedin.userservice.config.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * Server-side state for refresh tokens - the part that cannot live in the token itself.
 *
 * <p>Two keys per login:
 * <ul>
 *   <li>{@code auth:sid:{sessionId}} = ACTIVE - the session. Deleting it ends every token
 *       of that login at once (logout, or a detected replay).</li>
 *   <li>{@code auth:rt:{jti}} = sessionId - one refresh token. Consumed atomically on use,
 *       so each refresh token can be spent exactly once.</li>
 * </ul>
 *
 * <p>Both carry a TTL equal to the refresh lifetime, so Redis is the thing that expires
 * abandoned sessions - no cron job, no table growth.
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String SESSION_PREFIX = "auth:sid:";
    private static final String REFRESH_PREFIX = "auth:rt:";
    private static final String ACTIVE = "ACTIVE";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;

    /** Records a new login. Tokens issued before this exists are not accepted. */
    public void startSession(String sessionId) {
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, ACTIVE, refreshTtl());
    }

    public boolean isSessionActive(String sessionId) {
        return ACTIVE.equals(redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId));
    }

    /**
     * Ends a session: the session marker and every refresh token issued under it become
     * unusable. Access tokens already handed out stay valid until they expire - that is the
     * trade for not checking a store on every request.
     */
    public void endSession(String sessionId) {
        redisTemplate.delete(SESSION_PREFIX + sessionId);
    }

    public void storeRefreshToken(String jti, String sessionId) {
        redisTemplate.opsForValue().set(REFRESH_PREFIX + jti, sessionId, refreshTtl());
    }

    /**
     * Atomic GETDEL: two concurrent refreshes with the same token cannot both succeed, which
     * is what makes rotation a real one-time exchange rather than a check-then-act race.
     *
     * @return the owning session id, or null when the token was already used or revoked
     */
    public String consumeRefreshToken(String jti) {
        return redisTemplate.opsForValue().getAndDelete(REFRESH_PREFIX + jti);
    }

    private Duration refreshTtl() {
        return Duration.ofMillis(properties.refreshExpiration());
    }
}
