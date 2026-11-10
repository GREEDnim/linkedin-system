package com.linkedin.userservice.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Refuses to start a deployment that is still signing tokens with the secret committed to the
 * repository.
 *
 * <p>The default in application.yaml exists so a fresh clone runs without setup. That is
 * exactly why it is dangerous: it is public, so anyone can mint a valid token for any user id
 * against a service that kept it. A warning would be ignored; under the prod profile this fails
 * the boot, which is the only version of this check that cannot be missed.
 *
 * <p>Compared by value rather than by "is the property set", because the default is applied by
 * the placeholder in the configuration and is indistinguishable from an explicitly configured
 * copy.
 */
@Component
@Slf4j
public class SecretGuard {

    /** Must match the fallback in application.yaml (jwt.secret). */
    private static final String DEVELOPMENT_JWT_SECRET =
            "39NCVLkAJG+XR67PxwWkgP+P8fhYglztTCX/HMoOgPI=";

    private static final String PRODUCTION_PROFILE = "prod";

    public SecretGuard(JwtProperties jwtProperties, Environment environment) {

        if (!DEVELOPMENT_JWT_SECRET.equals(jwtProperties.secret())) {
            return;
        }

        if (environment.matchesProfiles(PRODUCTION_PROFILE)) {
            throw new IllegalStateException(
                    "Refusing to start with the prod profile: jwt.secret is the development key "
                            + "committed to the repository. Set JWT_SECRET to a private value "
                            + "(Base64, at least 32 bytes).");
        }

        log.warn("Using the development jwt.secret from application.yaml - acceptable locally, "
                + "never in a deployment. Set JWT_SECRET before deploying.");
    }
}
