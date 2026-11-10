package com.linkedin.apigateway.security;

/**
 * The complete list of paths that do not require a token.
 *
 * <p>Deliberately one small, boring, explicit class. An allow-list scattered across
 * annotations is how "temporarily public" endpoints become permanent: here every
 * entry sits next to its justification, and adding one is a visible diff.
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    /**
     * @param method the HTTP method, so {@code POST /auth/login} can be public
     *               without making the whole {@code /auth} tree public
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

        // The only two ways to obtain a token in the first place.
        if ("POST".equalsIgnoreCase(method)) {
            return path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/login");
        }

        return false;
    }
}
