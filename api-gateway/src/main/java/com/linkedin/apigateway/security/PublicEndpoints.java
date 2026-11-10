package com.linkedin.apigateway.security;

/**
 * The complete list of paths that do not require an access token.
 *
 * <p>Deliberately one small, boring, explicit class. An allow-list scattered across
 * annotations is how "temporarily public" endpoints become permanent: here every entry sits
 * next to its justification, and adding one is a visible diff.
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    /**
     * @param method the HTTP method, so {@code POST /auth/login} can be public without making
     *               the whole {@code /auth} tree public
     */
    public static boolean isPublic(String method, String path) {

        // CORS preflight carries no Authorization header by definition.
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Liveness/readiness probes.
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return true;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }

        return switch (path) {
            // Obtaining tokens in the first place.
            case "/api/v1/auth/register", "/api/v1/auth/login" -> true;
            // The caller's access token has expired by definition, so refresh cannot demand
            // one; it authenticates with the refresh token in its body instead.
            case "/api/v1/auth/refresh" -> true;
            // Logout must work when the short-lived token is already gone, and it is
            // deliberately idempotent: it revokes the session behind the refresh token.
            case "/api/v1/auth/logout" -> true;
            default -> false;
        };
    }
}
