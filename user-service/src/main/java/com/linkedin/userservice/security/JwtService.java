package com.linkedin.userservice.security;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.linkedin.userservice.config.JwtProperties;
import com.linkedin.userservice.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jwts.SIG;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and parses the platform's JWTs, and is the only place that knows their shape.
 *
 * <p>Claims follow the registered set wherever one exists, so the tokens are legible to
 * standard tooling instead of only to us:
 * <ul>
 *   <li>{@code sub} - the user id (was previously the email; {@code sub} is the subject
 *       of the token, and email is mutable)</li>
 *   <li>{@code iss} - {@code jwt.issuer}, which the gateway requires</li>
 *   <li>{@code iat}, {@code exp} - issued-at and expiry, both enforced on parse</li>
 *   <li>{@code jti} - unique per token; the refresh token's jti is what a store keys on
 *       so a token can be spent exactly once</li>
 *   <li>{@code sid} - our session id, constant across rotations, so a whole login can be
 *       revoked in one operation</li>
 *   <li>{@code type} - {@code access} or {@code refresh}. Without it a refresh token is
 *       just a long-lived access token, which is the hole this closes</li>
 *   <li>{@code email} - convenience for the UI, access token only</li>
 * </ul>
 */
@Component
public class JwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_SESSION = "sid";
    public static final String CLAIM_EMAIL = "email";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /**
     * Tolerance for clock drift between the issuer and the verifier. Zero tolerance means a
     * gateway whose clock is half a second ahead rejects tokens the moment they are minted.
     */
    private static final long CLOCK_SKEW_SECONDS = 30;

    /**
     * Sanity bounds for the configured lifetimes. They exist to catch the classic unit slip:
     * 900000 means fifteen minutes, while 15 means fifteen milliseconds - a token that is
     * already expired by the time it is serialised, which is indistinguishable from a broken
     * signature from the client's side.
     */
    private static final long MIN_ACCESS_LIFETIME_MS = 60_000L;
    private static final long MAX_REFRESH_LIFETIME_MS = 90L * 24 * 60 * 60 * 1000;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        validate(properties);
        this.properties = properties;
        // Throws WeakKeyException if the decoded secret is shorter than HS256 requires.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    /**
     * Configuration mistakes fail at startup, not as mysterious 401s in production: the
     * expirations are milliseconds added to {@code currentTimeMillis()}, so a value copied
     * from a tutorial in seconds yields tokens that expired long ago.
     */
    private static void validate(JwtProperties properties) {

        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("jwt.secret must be set (Base64, at least 32 bytes)");
        }
        if (properties.issuer() == null || properties.issuer().isBlank()) {
            throw new IllegalStateException("jwt.issuer must be set");
        }
        if (properties.expiration() < MIN_ACCESS_LIFETIME_MS) {
            throw new IllegalStateException(
                    "jwt.expiration is in MILLISECONDS and must be at least " + MIN_ACCESS_LIFETIME_MS
                            + " (900000 = 15 minutes), got " + properties.expiration());
        }
        if (properties.refreshExpiration() <= properties.expiration()) {
            throw new IllegalStateException(
                    "jwt.refresh-expiration must exceed jwt.expiration, got "
                            + properties.refreshExpiration() + " vs " + properties.expiration());
        }
        if (properties.refreshExpiration() > MAX_REFRESH_LIFETIME_MS) {
            throw new IllegalStateException(
                    "jwt.refresh-expiration looks like the wrong unit (max 90 days), got "
                            + properties.refreshExpiration());
        }
    }

    /**
     * Issues the pair for one session. The refresh token's jti is returned because the
     * caller has to register it with the store before the token can be used.
     */
    public TokenPair issue(User user, String sessionId) {

        Date now = new Date();

        String accessToken = Jwts.builder()
                .subject(user.getId())
                .issuer(properties.issuer())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_SESSION, sessionId)
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.expiration()))
                .signWith(signingKey, SIG.HS256)
                .compact();

        String refreshJti = UUID.randomUUID().toString();

        String refreshToken = Jwts.builder()
                .subject(user.getId())
                .issuer(properties.issuer())
                .id(refreshJti)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_SESSION, sessionId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.refreshExpiration()))
                .signWith(signingKey, SIG.HS256)
                .compact();

        return new TokenPair(accessToken, refreshToken, refreshJti, properties.expiration() / 1000);
    }

    /**
     * Verifies signature, issuer and expiry, then returns the claims.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with, expired
     *                                      or from a different issuer
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** @param accessTtlSeconds expiry of the access token, for the OAuth-style expires_in field */
    public record TokenPair(
            String accessToken,
            String refreshToken,
            String refreshJti,
            long accessTtlSeconds) {
    }
}
