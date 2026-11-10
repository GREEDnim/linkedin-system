package com.linkedin.apigateway.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.linkedin.apigateway.exception.GatewayErrorWriter;
import com.linkedin.apigateway.exception.TokenValidationException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The edge: every request that is not explicitly public must carry a valid access
 * token, and only this filter may decide who the caller is.
 *
 * <p>Runs as a plain servlet filter rather than as a Gateway route filter on
 * purpose. Authentication is cross-cutting: it has to apply to paths that match no
 * route at all, and it has to run before the route handler picks a backend. A
 * {@code OncePerRequestFilter} sits in front of the dispatcher, can short-circuit
 * with a 401, and stays unit-testable without a running gateway.
 *
 * <p><b>Header ownership is the actual security boundary.</b> The services trust
 * {@code X-User-Id} completely - they were written that way from the start and this
 * filter is what finally makes that trust reasonable. So this filter never forwards a
 * client-supplied value: it strips the header and re-adds it only from a verified
 * token. Without that, a caller could keep a valid token for their own account and
 * still act as anybody else.
 *
 * <p>Note what this does NOT do: the services remain reachable directly on their own
 * ports, so this guard applies only to traffic that enters through the gateway. Locking
 * the services down to loopback/private networking is the complementary half.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** The header every service reads to learn who is calling. */
    public static final String USER_ID_HEADER = "X-User-Id";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (PublicEndpoints.isPublic(request.getMethod(), path)) {
            // Still wrapped with a null user id: an anonymous caller must not be able
            // to inject an identity on the very endpoints that hand out tokens.
            filterChain.doFilter(new UserIdRequest(request, null), response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            GatewayErrorWriter.unauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();

        try {
            String userId = jwtVerifier.verifyAccessToken(token);
            filterChain.doFilter(new UserIdRequest(request, userId), response);
        } catch (TokenValidationException ex) {
            // Authentic token, wrong kind (e.g. a refresh token).
            log.debug("Rejected token for {}: {}", path, ex.getMessage());
            GatewayErrorWriter.unauthorized(response, ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            // Bad signature, malformed, or expired - jjwt's own diagnostics stay in
            // the debug log; the caller gets one generic reason.
            log.debug("Rejected token for {}: {}", path, ex.getMessage());
            GatewayErrorWriter.unauthorized(response, "Invalid or expired token");
        }
    }

    /**
     * Makes {@code X-User-Id} read-only from the servlet's point of view: whatever the
     * client sent is invisible to everything downstream, and the value present is the
     * one this filter decided on (absent entirely for public endpoints).
     */
    static final class UserIdRequest extends HttpServletRequestWrapper {

        private final String userId;

        UserIdRequest(HttpServletRequest request, String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId == null
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(userId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                String name = original.nextElement();
                if (!USER_ID_HEADER.equalsIgnoreCase(name)) {
                    names.add(name);
                }
            }
            if (userId != null) {
                names.add(USER_ID_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
