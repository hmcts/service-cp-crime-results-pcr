package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public record RetryServiceConfig(
        @Value("${service-bus.retry-durations}") List<Duration> delays,
        @Value("${service-bus.max-tries}") int maxTries) {

    public Duration delayFor(final int retryNumber) {
        final int index = Math.min(retryNumber - 1, delays.size() - 1);
        return delays.get(index);
    }
}