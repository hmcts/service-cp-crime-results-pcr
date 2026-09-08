package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class EntraAuthPropertiesTest {

    @Test
    void constructor_should_succeed_whenModeOff_blankTenantAndAudience_environmentUnknown() {
        assertThatNoException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", 60, 600, "UNKNOWN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "STE", "SIT", "PRP", "PRD"})
    void constructor_should_throw_whenModeOff_inADeployedEnvironment(final String environmentName) {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OFF, "tenant", "audience", "", "", 60, 600, environmentName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "STE", "SIT", "PRP", "PRD"})
    void constructor_should_throw_whenModeObserve_inADeployedEnvironment(final String environmentName) {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OBSERVE, "tenant", "audience", "", "", 60, 600, environmentName));
    }

    @Test
    void constructor_should_succeed_whenModeEnforce_inADeployedEnvironment_withTenantAndAudience() {
        assertThatNoException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "tenant", "audience", "", "", 60, 600, "PRD"));
    }

    @Test
    void constructor_should_throw_whenModeEnforce_andAudienceBlank() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "tenant", "", "", "", 60, 600, "UNKNOWN"));
    }

    @Test
    void constructor_should_throw_whenModeEnforce_andTenantIdBlank() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "", "audience", "", "", 60, 600, "UNKNOWN"));
    }

    @Test
    void getIssuer_should_derive_fromTenantId_whenBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "", 60, 600, "UNKNOWN");

        assertThat(properties.getIssuer()).isEqualTo("https://login.microsoftonline.com/my-tenant/v2.0");
    }

    @Test
    void getIssuer_should_useConfiguredValue_whenNotBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "https://issuer.example/v2.0", "", 60, 600, "UNKNOWN");

        assertThat(properties.getIssuer()).isEqualTo("https://issuer.example/v2.0");
    }

    @Test
    void getJwksUri_should_derive_fromIssuer_whenBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "", 60, 600, "UNKNOWN");

        assertThat(properties.getJwksUri())
                .isEqualTo("https://login.microsoftonline.com/my-tenant/v2.0/discovery/v2.0/keys");
    }

    @Test
    void getJwksUri_should_useConfiguredValue_whenNotBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "https://jwks.example/keys", 60, 600, "UNKNOWN");

        assertThat(properties.getJwksUri()).isEqualTo("https://jwks.example/keys");
    }

    @Test
    void getClockSkewSeconds_should_clampAt300_whenConfiguredAbove() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", 900, 600, "UNKNOWN");

        assertThat(properties.getClockSkewSeconds()).isEqualTo(300);
    }

    @Test
    void getClockSkewSeconds_should_returnConfiguredValue_whenBelow300() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", 45, 600, "UNKNOWN");

        assertThat(properties.getClockSkewSeconds()).isEqualTo(45);
    }
}
