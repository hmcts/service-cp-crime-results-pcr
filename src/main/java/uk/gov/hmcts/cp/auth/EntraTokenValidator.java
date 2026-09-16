package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies an Entra app-only bearer token per {@code docs/Authentication.md}: signature pinned
 * to a single algorithm against the configured JWKS, then every claim in the standard checked
 * manually so each rejection carries an exact {@link Reason} rather than a library-generic one.
 */
@Service
public class EntraTokenValidator {

    private static final String SUCCESS_METRIC = "cp.auth.success";
    private static final String FAILURE_METRIC = "cp.auth.failure";

    private final EntraAuthProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public EntraTokenValidator(final EntraAuthProperties properties,
                                final JWKSource<SecurityContext> jwkSource,
                                final MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.jwtProcessor = new DefaultJWTProcessor<>();
        // Single algorithm pinned in code — Entra's JWKS carries no `alg`, so this is the only
        // trustworthy source. Makes alg:none, algorithm confusion, and header-supplied key
        // material structurally impossible rather than separately defended.
        this.jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        // Claims are checked manually below so each rejection carries an exact Reason.
        this.jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> { });
    }

    public ValidatedCaller validate(final String rawToken) throws TokenValidationException {
        try {
            final JWTClaimsSet claims = verifySignature(rawToken);
            verifyClaims(claims);
            final ValidatedCaller caller = resolveIdentity(claims);
            meterRegistry.counter(SUCCESS_METRIC).increment();
            return caller;
        } catch (TokenValidationException e) {
            meterRegistry.counter(FAILURE_METRIC, "reason", e.getReason().name()).increment();
            throw e;
        }
    }

    private JWTClaimsSet verifySignature(final String rawToken) throws TokenValidationException {
        try {
            return jwtProcessor.process(rawToken, null);
        } catch (ParseException e) {
            throw new TokenValidationException(Reason.MALFORMED_TOKEN);
        } catch (BadJOSEException | JOSEException e) {
            throw new TokenValidationException(Reason.INVALID_SIGNATURE);
        }
    }

    private void verifyClaims(final JWTClaimsSet claims) throws TokenValidationException {
        requireExactMatch(claims.getIssuer(), properties.getIssuer(), Reason.UNTRUSTED_ISSUER);
        requireAudience(claims);
        requireValidExpiry(claims);
        requireNotYetValidRespected(claims);
        requireExactMatch(stringClaim(claims, "tid"), properties.getTenantId(), Reason.WRONG_TENANT);
        requireExactMatch(stringClaim(claims, "ver"), "2.0", Reason.WRONG_VERSION);
    }

    private ValidatedCaller resolveIdentity(final JWTClaimsSet claims) throws TokenValidationException {
        final UUID clientId = parseAuthorisedParty(claims);
        if (stringClaim(claims, "scp") != null) {
            throw new TokenValidationException(Reason.SCOPE_PRESENT);
        }
        final List<String> roles = rolesClaim(claims);
        if (roles.isEmpty()) {
            throw new TokenValidationException(Reason.MISSING_ROLES);
        }
        // App-only is proven by sub == oid, never by idtyp — Entra omits idtyp unless explicitly
        // enabled as an optional claim, so requiring it would reject all legitimate traffic.
        if (!stringEquals(claims.getSubject(), stringClaim(claims, "oid"))) {
            throw new TokenValidationException(Reason.DELEGATED_TOKEN);
        }
        return new ValidatedCaller(clientId, roles, true);
    }

    private UUID parseAuthorisedParty(final JWTClaimsSet claims) throws TokenValidationException {
        final String azp = stringClaim(claims, "azp");
        if (azp == null) {
            throw new TokenValidationException(Reason.MISSING_AZP);
        }
        try {
            return UUID.fromString(azp);
        } catch (IllegalArgumentException e) {
            throw new TokenValidationException(Reason.NON_UUID_AZP);
        }
    }

    private void requireAudience(final JWTClaimsSet claims) throws TokenValidationException {
        if (!claims.getAudience().contains(properties.getAudience())) {
            throw new TokenValidationException(Reason.UNTRUSTED_AUDIENCE);
        }
    }

    private void requireValidExpiry(final JWTClaimsSet claims) throws TokenValidationException {
        if (claims.getExpirationTime() == null) {
            throw new TokenValidationException(Reason.EXPIRED);
        }
        final Instant expiryWithSkew = claims.getExpirationTime().toInstant().plusSeconds(properties.getClockSkewSeconds());
        if (expiryWithSkew.isBefore(Instant.now())) {
            throw new TokenValidationException(Reason.EXPIRED);
        }
    }

    private void requireNotYetValidRespected(final JWTClaimsSet claims) throws TokenValidationException {
        if (claims.getNotBeforeTime() == null) {
            return;
        }
        final Instant notBeforeWithSkew = claims.getNotBeforeTime().toInstant().minusSeconds(properties.getClockSkewSeconds());
        if (notBeforeWithSkew.isAfter(Instant.now())) {
            throw new TokenValidationException(Reason.NOT_YET_VALID);
        }
    }

    private void requireExactMatch(final String actual, final String expected, final Reason reason)
            throws TokenValidationException {
        if (!stringEquals(actual, expected)) {
            throw new TokenValidationException(reason);
        }
    }

    private static boolean stringEquals(final String left, final String right) {
        return left != null && left.equals(right);
    }

    private static String stringClaim(final JWTClaimsSet claims, final String name) {
        return safeClaim(() -> claims.getStringClaim(name)).orElse(null);
    }

    private static List<String> rolesClaim(final JWTClaimsSet claims) {
        return safeClaim(() -> claims.getStringListClaim("roles")).orElse(List.of());
    }

    /** A present-but-wrong-type claim is treated as absent, never as a validation error in its
     * own right — the specific check that needed it (missing azp, missing roles, etc.) reports
     * the precise Reason instead. */
    private static <T> Optional<T> safeClaim(final ClaimReader<T> reader) {
        Optional<T> value;
        try {
            value = Optional.ofNullable(reader.read());
        } catch (ParseException e) {
            value = Optional.empty();
        }
        return value;
    }

    @FunctionalInterface
    private interface ClaimReader<T> {
        T read() throws ParseException;
    }
}
