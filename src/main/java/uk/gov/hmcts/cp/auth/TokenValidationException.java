package uk.gov.hmcts.cp.auth;

/**
 * A rejected bearer token. Checked — a caller must turn it into a 401/403, not a 500.
 * Constructed from a {@link Reason} only: the raw token must never reach this exception.
 */
public class TokenValidationException extends Exception {

    private static final long serialVersionUID = 1L;
    private static final String INVALID_TOKEN = "invalid_token";
    private static final String INSUFFICIENT_SCOPE = "insufficient_scope";

    private final Reason reason;

    public TokenValidationException(final Reason reason) {
        super(reason.description());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * RFC 6750 mapping: an authentication failure is 401/invalid_token; anything else is
     * 403/insufficient_scope — the caller is known, but not permitted.
     */
    public enum Reason {
        MISSING_HEADER(INVALID_TOKEN, "Authorization header is missing", true),
        MALFORMED_TOKEN(INVALID_TOKEN, "Bearer token is malformed", true),
        INVALID_SIGNATURE(INVALID_TOKEN, "Token signature is invalid", true),
        UNTRUSTED_AUDIENCE(INVALID_TOKEN, "Token audience is not trusted", true),
        UNTRUSTED_ISSUER(INVALID_TOKEN, "Token issuer is not trusted", true),
        EXPIRED(INVALID_TOKEN, "Token has expired", true),
        NOT_YET_VALID(INVALID_TOKEN, "Token is not yet valid", true),
        WRONG_TENANT(INVALID_TOKEN, "Token tenant does not match", true),
        WRONG_VERSION(INVALID_TOKEN, "Token version is not supported", true),
        MISSING_AZP(INVALID_TOKEN, "Token is missing the authorised-party claim", true),
        NON_UUID_AZP(INVALID_TOKEN, "Token authorised-party claim is not a UUID", true),
        DELEGATED_TOKEN(INSUFFICIENT_SCOPE, "Delegated tokens are not accepted", false),
        SCOPE_PRESENT(INSUFFICIENT_SCOPE, "Delegated scope claim is not accepted", false),
        MISSING_ROLES(INSUFFICIENT_SCOPE, "Token carries no roles", false);

        private final String code;
        private final String desc;
        private final boolean authenticationFailure;

        Reason(final String code, final String desc, final boolean authenticationFailure) {
            this.code = code;
            this.desc = desc;
            this.authenticationFailure = authenticationFailure;
        }

        public String errorCode() {
            return code;
        }

        public String description() {
            return desc;
        }

        public boolean isAuthenticationFailure() {
            return authenticationFailure;
        }
    }
}
