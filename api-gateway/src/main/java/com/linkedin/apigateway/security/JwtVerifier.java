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
 * <p>The contract is user-service's, and it is the registered JWT claim set wherever one
 * exists:
 * <ul>
 *   <li>HS256, signed with the shared Base64 secret ({@code jwt.secret})</li>
 *   <li>{@code iss} - must equal {@code jwt.issuer}</li>
 *   <li>{@code sub} - the user id, which becomes {@code X-User-Id}</li>
 *   <li>{@code type} - must be {@code access}</li>
 * </ul>
 *
 * <p>Signature, expiry and issuer are enforced by jjwt during parsing, so a token that
 * reaches the checks below is already authentic.
 */
@Component
public class JwtVerifier {

    private static final String CLAIM_TYPE = "type";
    private static final String ACCESS_TYPE = "access";

    /**
     * Clock drift tolerance, matching the issuer's own setting. Zero tolerance rejects
     * freshly minted tokens whenever the two hosts' clocks differ by a second.
     */
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final SecretKey signingKey;
    private final String issuer;

    public JwtVerifier(JwtProperties jwtProperties) {
        // Fail at startup, not on the first request, if the configuration is unusable.
        if (jwtProperties.issuer() == null || jwtProperties.issuer().isBlank()) {
            throw new IllegalStateException("jwt.issuer must be set and match the issuer in user-service");
        }
        this.issuer = jwtProperties.issuer();
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    /**
     * @return the authenticated user id
     * @throws TokenValidationException when the token is authentic but not usable as an access
     *                                  token
     * @throws io.jsonwebtoken.JwtException when it is malformed, tampered with, expired or from
     *                                      another issuer
     */
    public String verifyAccessToken(String token) {

        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // A refresh token is a validly signed token that lives for days. Accepting it here
        // would hand out a long-lived access credential and defeat having two token types at
        // all, so the kind of token is checked, not assumed.
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!ACCESS_TYPE.equals(type)) {
            throw new TokenValidationException("Token is not an access token");
        }

        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new TokenValidationException("Token carries no subject");
        }

        return userId;
    }
}
