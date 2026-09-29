package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;

import static org.assertj.core.api.Assertions.assertThat;

class TokenValidationExceptionTest {

    @ParameterizedTest
    @EnumSource(Reason.class)
    void reason_should_mapErrorCodeToAuthenticationFailureFlag_consistently(final Reason reason) {
        if (reason.isAuthenticationFailure()) {
            assertThat(reason.errorCode()).isEqualTo("invalid_token");
        } else {
            assertThat(reason.errorCode()).isEqualTo("insufficient_scope");
        }
    }

    @Test
    void constructor_should_exposeReason() {
        final TokenValidationException exception = new TokenValidationException(Reason.EXPIRED);

        assertThat(exception.getReason()).isEqualTo(Reason.EXPIRED);
    }

    @Test
    void constructor_should_takeOnlyAReason_soTokenMaterialCanNeverBeInjected() {
        final TokenValidationException exception = new TokenValidationException(Reason.INVALID_SIGNATURE);

        assertThat(exception.getMessage()).isEqualTo(Reason.INVALID_SIGNATURE.description());
    }
}
