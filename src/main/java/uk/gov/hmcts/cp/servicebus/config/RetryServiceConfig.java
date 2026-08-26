package uk.gov.hmcts.cp.servicebus.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@Getter
public class RetryServiceConfig {

    private final List<Duration> retryDelays;

    public RetryServiceConfig(@Value("${service-bus.retry-durations}") final List<Duration> retryDelays) {
        Assert.notEmpty(retryDelays, "service-bus.retry-durations must not be empty");
        Assert.isTrue(retryDelays.stream().noneMatch(Duration::isNegative),
                "service-bus.retry-durations must not contain negative durations");
        log.info("RetryServiceConfig using retryDelays {}", retryDelays);
        this.retryDelays = retryDelays;
    }
}
