package uk.gov.hmcts.cp.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the real filter chain, not just {@code EntraTokenValidatorTest}'s unit-level checks —
 * runs with {@code auth.mode=ENFORCE} and an in-process JWKS registered under its own bean name
 * marked {@code @Primary} (never an override-by-name — a wrong-JWKS-in-use bug is otherwise
 * undetectable, since every negative case still gets its rejection, just from a failed lookup
 * against the real Entra endpoint instead of the check it exists to prove).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresInitialise.class)
@TestPropertySource(properties = {
        "service-bus.auto-start-processors=false",
        "auth.mode=ENFORCE",
        "auth.tenant-id=11111111-1111-1111-1111-111111111111",
        "auth.audience=22222222-2222-2222-2222-222222222222"
})
class EntraAuthIntegrationTest {

    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    private static final String KEY_ID = "integration-test-key";
    private static final String AZP = "33333333-3333-3333-3333-333333333333";
    private static final String OID = "44444444-4444-4444-4444-444444444444";
    private static final String OTHER_AUDIENCE = "55555555-5555-5555-5555-555555555555";
    private static final String CASE_URN = "ABCD1234567";

    private static RSAKey signingKey;

    @Resource
    private MockMvc mockMvc;

    @BeforeAll
    static void mintSigningKey() throws JOSEException {
        // Runs before the Spring context loads (JUnit5 @BeforeAll precedes SpringExtension's
        // context bootstrap), so the key is ready when TestJwksConfig's @Bean method runs.
        signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
    }

    @TestConfiguration
    static class TestJwksConfig {
        @Bean("testJwkSource")
        @Primary
        JWKSource<SecurityContext> testJwkSource() {
            return new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
        }
    }

    @Test
    void exemptEndpoint_should_answerWithoutToken_throughTheRealFilterChain() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_should_reject401_whenTokenAbsent_throughTheRealFilterChain() throws Exception {
        mockMvc.perform(get("/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        CASE_URN, "00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000022"))
                .andExpect(status().isUnauthorized());
    }

    @Test
        // The one assertion able to fail if the wrong JWKS is wired — every negative case above
        // would still pass even against the real Entra endpoint, just for the wrong reason.
    void protectedEndpoint_should_accept200_whenTokenMintedByThisTest() throws Exception {
        mockMvc.perform(get("/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        CASE_URN, "00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000022")
                        .header("Authorization", "Bearer " + mint(b -> { })))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_should_reject401_whenTokenExpired() throws Exception {
        final String token = mint(b -> b.expirationTime(Date.from(Instant.now().minusSeconds(1000))));

        mockMvc.perform(get("/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        CASE_URN, "00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000022")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_should_reject401_whenAudienceWrong() throws Exception {
        final String token = mint(b -> b.audience(OTHER_AUDIENCE));

        mockMvc.perform(get("/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        CASE_URN, "00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000022")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static String mint(final Consumer<JWTClaimsSet.Builder> customizer) throws JOSEException {
        final Instant now = Instant.now();
        final JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(Date.from(now.plusSeconds(300)))
                .notBeforeTime(Date.from(now.minusSeconds(60)))
                .subject(OID)
                .claim("tid", TENANT_ID)
                .claim("ver", "2.0")
                .claim("oid", OID)
                .claim("azp", AZP)
                .claim("roles", List.of("PcrReader"));
        customizer.accept(builder);
        final SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), builder.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
