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
        return EXEMPT_PATHS.contains(path);
    }
}
