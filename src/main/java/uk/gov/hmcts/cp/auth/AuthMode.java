package uk.gov.hmcts.cp.auth;

/**
 * Entra token validation rollout mode.
 */
public enum AuthMode {
    /** No validation; identity from an unverified claim. Must never run in a deployed environment. */
    OFF,
    /** Validated and every failure logged/counted, but no request is rejected. Provides no protection. */
    OBSERVE,
    /** Validated, and invalid requests rejected. */
    ENFORCE
}
