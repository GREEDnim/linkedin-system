package com.linkedin.apigateway.security;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.linkedin.apigateway.config.JwtProperties;
import com.linkedin.apigateway.exception.TokenValidationException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies access tokens issued by user-service and returns the user id to forward.
 *
 * <p>The contract is user-service's, not this service's invention:
 * <ul>
 *   <li>HS256, signed with the shared Base64 secret ({@code jwt.secret})</li>
 *   <li>{@code userId} claim - the value that becomes {@code X-User-Id}</li>
 *   <li>{@code type} claim - must be {@code access}</li>
 * </ul>
 *
 * <p>Signature and expiry are enforced by jjwt during parsing, so a token that
 * reaches the checks below is already authentic.
 */
@Component
public class JwtVerifier {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TYPE = "type";
    private static final String ACCESS_TYPE = "access";

    private final SecretKey signingKey;

    public JwtVerifier(JwtProperties jwtProperties) {
        // Fail at startup, not on the first request, if the secret is unusable.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    /**
     * @return the authenticated user id
     * @throws TokenValidationException when the token is authentic but not usable as
     *                                  an access token
     * @throws io.jsonwebtoken.JwtException when it is malformed, tampered with or expired
     */
    public String verifyAccessToken(String token) {

        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // A refresh token is a validly signed token that lives for days. Accepting it
        // here would hand out a long-lived access credential and defeat having two
        // token types at all - the exact hole we closed inside user-service.
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!ACCESS_TYPE.equals(type)) {
            throw new TokenValidationException("Token is not an access token");
        }

        String userId = claims.get(CLAIM_USER_ID, String.class);
        if (userId == null || userId.isBlank()) {
            throw new TokenValidationException("Token carries no userId claim");
        }

        return userId;
    }
}
