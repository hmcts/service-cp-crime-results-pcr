package uk.gov.hmcts.cp.servicebus.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.servicebus.config.RetryServiceConfig;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBusRetryServiceTest {

    @Mock
    private RetryServiceConfig retryServiceConfig;
    @Mock
    private ClockService clockService;

    @InjectMocks
    private ServiceBusRetryService retryService;

    @Test
    void getRetryDelay_should_returnDelayForAttempt_whenWithinConfiguredRange() {
        when(retryServiceConfig.getRetryDelays()).thenReturn(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)));

        assertThat(retryService.getRetryDelay(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(retryService.getRetryDelay(2)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void getRetryDelay_should_returnLastConfiguredDelay_whenAttemptExceedsConfiguredRange() {
        when(retryServiceConfig.getRetryDelays()).thenReturn(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)));

        assertThat(retryService.getRetryDelay(5)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void getNextTryTime_should_returnNowPlusRetryDelay() {
        final OffsetDateTime now = OffsetDateTime.parse("2026-07-28T10:00:00Z");
        when(retryServiceConfig.getRetryDelays()).thenReturn(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)));
        when(clockService.nowOffsetUTC()).thenReturn(now);

        assertThat(retryService.getNextTryTime(1)).isEqualTo(now.plusSeconds(2));
        assertThat(retryService.getNextTryTime(2)).isEqualTo(now.plusSeconds(4));
    }
}
