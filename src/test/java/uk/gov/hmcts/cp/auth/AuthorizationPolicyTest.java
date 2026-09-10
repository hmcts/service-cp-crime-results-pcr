package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "/", "/actuator", "/actuator/health", "/actuator/health/liveness",
            "/actuator/health/readiness", "/actuator/info", "/actuator/prometheus"
    })
    void isExempt_should_returnTrue_forEnumeratedInfrastructurePaths(final String path) {
        assertThat(policy.isExempt(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/healthx", "/actuatorx", "/actuator/health/",
            "/cases/URN123/hearings/00000000-0000-0000-0000-000000000001/defendants/00000000-0000-0000-0000-000000000002"
    })
    void isExempt_should_returnFalse_forNonExemptOrNearMissPaths(final String path) {
        assertThat(policy.isExempt(path)).isFalse();
    }
}
