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
 * against a service that kept it.
 *
 * <p>A warning would be ignored, and a check that fires only under a {@code prod} profile
 * protects only the deployments that remembered to declare themselves. The one that matters is
 * the deployment that forgot, and it boots with no profile at all - so the check is inverted: the
 * committed key is accepted only while the {@code dev} profile is active. Silence is treated as
 * production.
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

    private static final String DEVELOPMENT_PROFILE = "dev";

    public SecretGuard(JwtProperties jwtProperties, Environment environment) {

        if (!DEVELOPMENT_JWT_SECRET.equals(jwtProperties.secret())) {
            // A real secret is configured. Nothing to guard against.
            return;
        }

        if (environment.matchesProfiles(DEVELOPMENT_PROFILE)) {
            log.warn("Using the development jwt.secret from application.yaml under the dev "
                    + "profile - never deploy with it. Set JWT_SECRET to override.");
            return;
        }

        throw new IllegalStateException(
                "Refusing to start: jwt.secret is the development key committed to the repository, "
                        + "and the 'dev' profile is not active. Set JWT_SECRET to a private value "
                        + "(Base64, at least 32 bytes), or activate 'dev' for local work "
                        + "(SPRING_PROFILES_ACTIVE=dev).");
    }
}
