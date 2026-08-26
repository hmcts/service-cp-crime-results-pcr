package uk.gov.hmcts.cp.servicebus.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.RetryServiceConfig;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ServiceBusRetryService {

    private final RetryServiceConfig retryServiceConfig;
    private final ClockService clockService;

    public Duration getRetryDelay(final int attempt) {
        final List<Duration> retryDelays = retryServiceConfig.getRetryDelays();
        final int index = attempt - 1 < retryDelays.size() ? attempt - 1 : retryDelays.size() - 1;
        final Duration delay = retryDelays.get(Math.max(index, 0));
        log.info("retry delay {}", delay);
        return delay;
    }

    public OffsetDateTime getNextTryTime(final int attempt) {
        return clockService.nowOffsetUTC().plus(getRetryDelay(attempt));
    }
}
