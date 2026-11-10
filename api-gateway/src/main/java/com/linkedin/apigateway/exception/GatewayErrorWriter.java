package com.linkedin.apigateway.exception;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes errors raised by the filter chain, which runs before Spring MVC and therefore
 * cannot use {@code @ExceptionHandler}.
 *
 * <p>The body is built from {@link ErrorResponse} by hand so the gateway stays
 * independent of whichever Jackson generation is on the classpath, while still emitting
 * exactly the fields the services emit - one error parser on the client side.
 */
public final class GatewayErrorWriter {

    private GatewayErrorWriter() {
    }

    public static void unauthorized(HttpServletResponse response, String message) throws IOException {
        write(response, ErrorResponse.of(HttpStatus.UNAUTHORIZED, message));
    }

    public static void tooManyRequests(HttpServletResponse response, String message) throws IOException {
        write(response, ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, message));
    }

    private static void write(HttpServletResponse response, ErrorResponse body) throws IOException {
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"details\":[]}",
                body.timestamp(),
                body.status(),
                body.error(),
                escape(body.message())));
    }

    /** Only the quotes need escaping: messages are fixed strings written by this service. */
    private static String escape(String message) {
        return message == null ? "Unauthorized" : message.replace('"', '\'');
    }
}
