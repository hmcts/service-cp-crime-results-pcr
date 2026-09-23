package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class EntraAuthPropertiesTest {

    @Test
    void constructor_should_succeed_whenModeOff_blankTenantAudienceAndRoles_environmentUnknown() {
        assertThatNoException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", "", 60, 600, "UNKNOWN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "STE", "SIT", "PRP", "PRD"})
    void constructor_should_throw_whenModeOff_inADeployedEnvironment(final String environmentName) {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OFF, "tenant", "audience", "app.read", "", "", 60, 600, environmentName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "STE", "SIT", "PRP", "PRD"})
    void constructor_should_throw_whenModeObserve_inADeployedEnvironment(final String environmentName) {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OBSERVE, "tenant", "audience", "app.read", "", "", 60, 600, environmentName));
    }

    @Test
    void constructor_should_succeed_whenModeEnforce_inADeployedEnvironment_withTenantAudienceAndRoles() {
        assertThatNoException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "tenant", "audience", "app.read", "", "", 60, 600, "PRD"));
    }

    @Test
    void constructor_should_throw_whenModeEnforce_andAudienceBlank() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "tenant", "", "app.read", "", "", 60, 600, "UNKNOWN"));
    }

    @Test
    void constructor_should_throw_whenModeEnforce_andTenantIdBlank() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "", "audience", "app.read", "", "", 60, 600, "UNKNOWN"));
    }

    @Test
    void constructor_should_throw_whenModeEnforce_andRolesBlank() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.ENFORCE, "tenant", "audience", "", "", "", 60, 600, "UNKNOWN"));
    }

    @Test
    void getIssuer_should_derive_fromTenantId_whenBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "", "", 60, 600, "UNKNOWN");

        assertThat(properties.getIssuer()).isEqualTo("https://login.microsoftonline.com/my-tenant/v2.0");
    }

    @Test
    void getIssuer_should_useConfiguredValue_whenNotBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "https://issuer.example/v2.0", "", 60, 600, "UNKNOWN");

        assertThat(properties.getIssuer()).isEqualTo("https://issuer.example/v2.0");
    }

    @Test
    void getJwksUri_should_derive_fromTenantId_whenBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "", "", 60, 600, "UNKNOWN");

        assertThat(properties.getJwksUri())
                .isEqualTo("https://login.microsoftonline.com/my-tenant/discovery/v2.0/keys");
    }

    @Test
    void getJwksUri_should_useConfiguredValue_whenNotBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "my-tenant", "", "", "", "https://jwks.example/keys", 60, 600, "UNKNOWN");

        assertThat(properties.getJwksUri()).isEqualTo("https://jwks.example/keys");
    }

    @Test
    void constructor_should_throw_whenClockSkewAbove300() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", "", 900, 600, "UNKNOWN"));
    }

    @Test
    void constructor_should_throw_whenClockSkewNegative() {
        assertThatIllegalStateException().isThrownBy(() ->
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", "", -1, 600, "UNKNOWN"));
    }

    @Test
    void getClockSkewSeconds_should_returnConfiguredValue_whenWithinBounds() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", "", 45, 600, "UNKNOWN");

        assertThat(properties.getClockSkewSeconds()).isEqualTo(45);
    }

    @Test
    void getRoles_should_parseCommaSeparatedList_trimmedAndFilteredForBlankEntries() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "", "", " app.read , app.write ,,", "", "", 60, 600, "UNKNOWN");

        assertThat(properties.getRoles()).isEqualTo(Set.of("app.read", "app.write"));
    }

    @Test
    void getRoles_should_beEmpty_whenBlank() {
        final EntraAuthProperties properties =
                new EntraAuthProperties(AuthMode.OFF, "", "", "", "", "", 60, 600, "UNKNOWN");

        assertThat(properties.getRoles()).isEmpty();
    }
}