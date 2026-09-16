package uk.gov.hmcts.cp.auth;

import java.util.List;
import java.util.UUID;

/**
 * The caller identity resolved from a bearer token — verified via signature/claims checks,
 * or unverified (mode OFF/OBSERVE, or a failure in OBSERVE mode).
 */
public record ValidatedCaller(UUID clientId, List<String> roles, boolean verified) {
}
