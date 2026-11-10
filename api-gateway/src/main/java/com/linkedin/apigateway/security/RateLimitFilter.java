package com.linkedin.apigateway.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.linkedin.apigateway.config.RateLimitProperties;
import com.linkedin.apigateway.exception.GatewayErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixed-window rate limiting at the edge, backed by Redis so the limit is shared by every
 * gateway instance rather than being per-process.
 *
 * <p>Runs before authentication ({@code @Order(10)} vs 20) on purpose: the endpoints that
 * need protecting most are the ones that are deliberately public.
 *
 * <p>A fixed window is the simple option and it has a known weakness: a client can send its
 * full budget at the end of one window and again at the start of the next, briefly doubling
 * the rate. That is acceptable for login throttling; a sliding window or token bucket (which
 * Spring Cloud Gateway ships as Bucket4j) is the upgrade if the limit ever needs to be exact.
 *
 * <p>The counter is keyed by client address. That is the correct identity at the edge - the
 * gateway is the first hop, so {@code remoteAddr} has not been rewritten - but it does mean
 * clients behind one NAT share a budget. If the gateway is ever placed behind a load
 * balancer, this should read the forwarded address from that balancer instead, and only from
 * it, since the header is otherwise client-controlled.
 */
@Component
@Order(10)
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, java.io.IOException {

        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean auth = path.startsWith(AUTH_PATH_PREFIX);
        int limit = auth ? properties.authRequestsPerMinute() : properties.apiRequestsPerMinute();
        long window = Instant.now().getEpochSecond() / WINDOW.toSeconds();
        String key = KEY_PREFIX + (auth ? "auth:" : "api:") + clientAddress(request) + ":" + window;

        long used;
        try {
            Long incremented = redisTemplate.opsForValue().increment(key);
            used = incremented == null ? 0 : incremented;
            if (used == 1) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (RuntimeException ex) {
            // Fail open: a Redis outage must not take authentication down with it. The limit
            // is a shield, not a dependency - losing it degrades protection, refusing every
            // request removes the product.
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (used > limit) {
            long retryAfter = WINDOW.toSeconds() - (Instant.now().getEpochSecond() % WINDOW.toSeconds());
            log.info("Rate limit exceeded for {} on {} ({}/{}), retry in {}s",
                    clientAddress(request), auth ? "auth" : "api", used, limit, retryAfter);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            GatewayErrorWriter.tooManyRequests(response,
                    "Too many requests - retry in " + retryAfter + " seconds");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
