package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.jwk.source.JWKSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntraTokenValidatorTest {

    private static final String KEY_ID = "test-key-1";
    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    private static final String AZP = "33333333-3333-3333-3333-333333333333";
    private static final String OID = "44444444-4444-4444-4444-444444444444";
    private static final String GRAPH_AUDIENCE = "00000003-0000-0000-c000-000000000000";
    private static final String SIBLING_AUDIENCE = "55555555-5555-5555-5555-555555555555";
    private static final String OTHER_TENANT_ID = "66666666-6666-6666-6666-666666666666";

    private static RSAKey signingKey;
    private static EntraTokenValidator validator;

    @BeforeAll
    static void setUpValidator() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        final JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, ISSUER, "", 60, 600, "PRD");
        validator = new EntraTokenValidator(properties, jwkSource, new SimpleMeterRegistry());
    }

    private static JWTClaimsSet.Builder baseClaims() {
        final Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(Date.from(now.plusSeconds(300)))
                .notBeforeTime(Date.from(now.minusSeconds(60)))
                .issueTime(Date.from(now.minusSeconds(60)))
                .subject(OID)
                .claim("tid", TENANT_ID)
                .claim("ver", "2.0")
                .claim("oid", OID)
                .claim("azp", AZP)
                .claim("roles", List.of("PcrReader"));
    }

    private static String mint(final Consumer<JWTClaimsSet.Builder> customizer) throws JOSEException {
        return mintWithHeader(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID), customizer);
    }

    private static String mintWithHeader(final JWSHeader.Builder header, final Consumer<JWTClaimsSet.Builder> customizer)
            throws JOSEException {
        final JWTClaimsSet.Builder builder = baseClaims();
        customizer.accept(builder);
        final SignedJWT jwt = new SignedJWT(header.build(), builder.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static void assertReason(final String token, final Reason expected) {
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(expected);
    }

    // --- Acceptance -----------------------------------------------------

    @Test
    void validate_should_accept_wellFormedAppOnlyToken_andResolveClientIdFromAzp_neverOid() throws Exception {
        final ValidatedCaller caller = validator.validate(mint(b -> { }));

        assertThat(caller.clientId()).isEqualTo(UUID.fromString(AZP));
        assertThat(caller.verified()).isTrue();
        assertThat(caller.roles()).containsExactly("PcrReader");
    }

    @Test
        // idtyp is opt-in on the app registration; Entra omits it by default. Requiring it would
        // reject all real traffic — this is the highest-value regression test in the suite.
    void validate_should_accept_tokenWithoutIdtypClaim() {
        assertThatCode(() -> validator.validate(mint(b -> { }))).doesNotThrowAnyException();
    }

    @Test
    void validate_should_tolerateExpiry_withinConfiguredClockSkew() throws Exception {
        final String token = mint(b -> b.expirationTime(Date.from(Instant.now().minusSeconds(30))));

        assertThatCode(() -> validator.validate(token)).doesNotThrowAnyException();
    }

    // --- Header and scheme handling --------------------------------------

    @Test
    void validate_should_reject_structurallyMalformedToken() {
        assertReason("not-a-jwt", Reason.MALFORMED_TOKEN);
    }

    @Test
    void validate_should_reject_tokenWithNonJsonPayload() {
        assertReason("aGVhZGVy.bm90LWpzb24.c2ln", Reason.MALFORMED_TOKEN);
    }

    // --- Signature — the attack cases ------------------------------------

    @Test
    void validate_should_reject_unsignedToken_algNone() {
        final PlainJWT unsigned = new PlainJWT(baseClaims().build());

        assertReason(unsigned.serialize(), Reason.INVALID_SIGNATURE);
    }

    @Test
        // Algorithm confusion: the RSA public key's modulus is public, so signing HS256 with it
        // must be rejected — a validator that reads `alg` from the header accepts this.
    void validate_should_reject_hs256SignedWithJwksRsaPublicKey_algorithmConfusion() throws Exception {
        final byte[] publicKeyAsSecret = signingKey.toRSAPublicKey().getModulus().toByteArray();
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).build(), baseClaims().build());
        jwt.sign(new MACSigner(publicKeyAsSecret));

        assertReason(jwt.serialize(), Reason.INVALID_SIGNATURE);
    }

    @Test
    void validate_should_reject_tokenSignedByAnUnrelatedKey() throws Exception {
        final RSAKey otherKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), baseClaims().build());
        jwt.sign(new RSASSASigner(otherKey));

        assertReason(jwt.serialize(), Reason.INVALID_SIGNATURE);
    }

    @Test
    void validate_should_reject_tamperedSignature() throws Exception {
        final String token = mint(b -> { });
        final String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertReason(tampered, Reason.INVALID_SIGNATURE);
    }

    @Test
    void validate_should_reject_unknownKeyId() throws Exception {
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("unknown-kid").build(), baseClaims().build());
        jwt.sign(new RSASSASigner(signingKey));

        assertReason(jwt.serialize(), Reason.INVALID_SIGNATURE);
    }

    @Test
    void validate_should_reject_unsupportedCriticalHeader() throws Exception {
        final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .criticalParams(Set.of("crit-param"))
                .customParam("crit-param", "x")
                .build();
        final SignedJWT jwt = new SignedJWT(header, baseClaims().build());
        jwt.sign(new RSASSASigner(signingKey));

        assertReason(jwt.serialize(), Reason.INVALID_SIGNATURE);
    }

    @Test
        // jku/jwk/x5u must never nominate the verifying key — only the configured JWKS may.
    void validate_should_ignore_keyMaterialSuppliedInHeader() throws Exception {
        final RSAKey attackerKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .jwk(attackerKey.toPublicJWK())
                .build();
        final SignedJWT jwt = new SignedJWT(header, baseClaims().build());
        jwt.sign(new RSASSASigner(attackerKey));

        assertReason(jwt.serialize(), Reason.INVALID_SIGNATURE);
    }

    // --- Audience and issuer ----------------------------------------------

    @Test
    void validate_should_reject_microsoftGraphAudience() throws Exception {
        assertReason(mint(b -> b.audience(GRAPH_AUDIENCE)), Reason.UNTRUSTED_AUDIENCE);
    }

    @Test
    void validate_should_reject_siblingApiAudience() throws Exception {
        assertReason(mint(b -> b.audience(SIBLING_AUDIENCE)), Reason.UNTRUSTED_AUDIENCE);
    }

    @Test
    void validate_should_reject_wrongIssuer() throws Exception {
        assertReason(mint(b -> b.issuer("https://login.microsoftonline.com/" + OTHER_TENANT_ID + "/v2.0")),
                Reason.UNTRUSTED_ISSUER);
    }

    @Test
    void validate_should_reject_issuerPrefixAttack() throws Exception {
        assertReason(mint(b -> b.issuer(ISSUER + ".attacker.example")), Reason.UNTRUSTED_ISSUER);
    }

    // --- Time and tenancy --------------------------------------------------

    @Test
    void validate_should_reject_expiredToken() throws Exception {
        assertReason(mint(b -> b.expirationTime(Date.from(Instant.now().minusSeconds(1000)))), Reason.EXPIRED);
    }

    @Test
    void validate_should_reject_tokenWithoutExp() throws Exception {
        assertReason(mint(b -> b.expirationTime(null)), Reason.EXPIRED);
    }

    @Test
    void validate_should_reject_notYetValidToken() throws Exception {
        assertReason(mint(b -> b.notBeforeTime(Date.from(Instant.now().plusSeconds(1000)))), Reason.NOT_YET_VALID);
    }

    @Test
    void validate_should_reject_wrongTenantId() throws Exception {
        assertReason(mint(b -> b.claim("tid", OTHER_TENANT_ID)), Reason.WRONG_TENANT);
    }

    @Test
    void validate_should_reject_v1TokenVersion() throws Exception {
        assertReason(mint(b -> b.claim("ver", "1.0")), Reason.WRONG_VERSION);
    }

    // --- Identity and app-only ----------------------------------------------

    @Test
    void validate_should_reject_missingAuthorisedPartyClaim() throws Exception {
        assertReason(mint(b -> b.claim("azp", null)), Reason.MISSING_AZP);
    }

    @Test
    void validate_should_reject_nonUuidAuthorisedPartyClaim() throws Exception {
        assertReason(mint(b -> b.claim("azp", "not-a-uuid")), Reason.NON_UUID_AZP);
    }

    @Test
    void validate_should_reject_delegatedToken_subDiffersFromOid() throws Exception {
        assertReason(mint(b -> b.subject("77777777-7777-7777-7777-777777777777")), Reason.DELEGATED_TOKEN);
    }

    @Test
    void validate_should_reject_tokenCarryingScopeClaim() throws Exception {
        assertReason(mint(b -> b.claim("scp", "user_impersonation")), Reason.SCOPE_PRESENT);
    }

    @Test
    void validate_should_reject_tokenWithoutRoles() throws Exception {
        assertReason(mint(b -> b.claim("roles", null)), Reason.MISSING_ROLES);
    }

    @Test
    void validate_should_reject_tokenWithEmptyRolesArray() throws Exception {
        assertReason(mint(b -> b.claim("roles", List.of())), Reason.MISSING_ROLES);
    }

    // --- Leakage -------------------------------------------------------------

    @Test
    void validate_should_neverLeakTokenMaterial_inRejectionException() {
        final String token = "not-a-jwt-and-definitely-secret-looking";

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(TokenValidationException.class)
                .hasMessageNotContaining(token);
    }
}
