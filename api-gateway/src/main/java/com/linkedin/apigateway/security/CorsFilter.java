package com.linkedin.apigateway.security;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.linkedin.apigateway.config.CorsProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CORS for the browser client, applied to every response - including rejections.
 *
 * <p>Runs before everything else ({@code @Order(5)}) for one reason: a 401 or 429 without CORS
 * headers is invisible to JavaScript. The browser reports "blocked by CORS policy" and the
 * application never sees the status, so the client cannot tell "your token expired" from
 * "this API is unreachable". Setting the headers in a filter that runs before authentication
 * means the real status survives to the browser.
 *
 * <p>Preflight is answered here and never forwarded: {@code OPTIONS} with
 * {@code Access-Control-Request-Method} is a question about policy, not a request for data.
 *
 * <p>{@code Access-Control-Allow-Credentials} is intentionally not set. Auth travels in the
 * Authorization header, so the browser never needs to attach cookies, and allowing credentials
 * would only widen what a successful cross-origin attack could do.
 */
@Component
@Order(5)
@Slf4j
@RequiredArgsConstructor
public class CorsFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * Deliberately does not include X-User-Id: the browser has no business sending it, and the
     * gateway overwrites it anyway.
     */
    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type", "Accept", "X-Requested-With");

    private static final String MAX_AGE_SECONDS = "3600";

    private final CorsProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, java.io.IOException {

        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (origin == null || !isAllowed(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Vary keeps a shared cache from serving one origin's response to another.
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);

        boolean preflight = HttpMethod.OPTIONS.matches(request.getMethod())
                && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;

        if (preflight) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(",", ALLOWED_METHODS));
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, String.join(",", ALLOWED_HEADERS));
            response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE_SECONDS);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String origin) {
        return properties.allowedOrigins() != null && properties.allowedOrigins().contains(origin);
    }
}
