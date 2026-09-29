package uk.gov.hmcts.cp.auth;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Endpoint exemption list — enumerated, never prefix-matched, so a newly added endpoint stays
 * protected until someone deliberately classifies it. Infrastructure paths only; this API's
 * one real endpoint carries case data and is never exempt.
 */
@Service
public class AuthorizationPolicy {

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/",
            "/actuator",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus");

    public boolean isExempt(final String path) {
        return EXEMPT_PATHS.contains(stripTrailingSlash(path));
    }

    /** Spring serves health at {@code /actuator/health/} as well, so a trailing slash must not defeat the match. */
    private static String stripTrailingSlash(final String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
