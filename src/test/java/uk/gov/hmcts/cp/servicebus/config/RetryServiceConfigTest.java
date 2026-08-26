package uk.gov.hmcts.cp.servicebus.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryServiceConfigTest {

    @Test
    void constructor_should_exposeConfiguredDelays_whenValid() {
        final RetryServiceConfig config = new RetryServiceConfig(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)));

        assertThat(config.getRetryDelays()).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void constructor_should_throwIllegalArgumentException_whenDelaysEmpty() {
        assertThatThrownBy(() -> new RetryServiceConfig(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_throwIllegalArgumentException_whenAnyDelayNegative() {
        assertThatThrownBy(() -> new RetryServiceConfig(List.of(Duration.ofSeconds(2), Duration.ofSeconds(-1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
