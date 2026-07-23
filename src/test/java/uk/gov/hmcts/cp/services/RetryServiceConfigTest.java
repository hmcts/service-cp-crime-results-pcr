package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.config.RetryServiceConfig;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetryServiceConfigTest {

    private final RetryServiceConfig schedule =
            new RetryServiceConfig(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)), 3);

    @Test
    void delayFor_should_returnDelayAtRetryNumberMinusOne() {
        assertThat(schedule.delayFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(schedule.delayFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(schedule.delayFor(3)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void delayFor_should_capAtLastDelay_whenRetryNumberExceedsDelayListSize() {
        // HRDS's ServiceBusRetryService.getRetryDelay behaviour: maxTries can exceed the
        // duration list length — the last entry is reused, not an index error.
        final RetryServiceConfig longerMaxTries =
                new RetryServiceConfig(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)), 5);

        assertThat(longerMaxTries.delayFor(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(longerMaxTries.delayFor(5)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void maxTries_should_beIndependentOfDelayListSize() {
        // maxTries can be shorter than the duration list too — e.g. a longer schedule
        // configured but capped earlier by a lower max-tries. Callers compare retryNumber
        // against maxTries() directly (HRDS's own inline shape) — no isExhausted wrapper.
        final RetryServiceConfig shorterMaxTries =
                new RetryServiceConfig(List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3)), 3);

        assertThat(shorterMaxTries.maxTries()).isEqualTo(3);
    }
}