package uk.gov.hmcts.cp.servicebus.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Getter
public class ServiceBusProperties {

    private static final String HTTPS = "https";

    // Fixed, not environment config — Terraform provisions this queue, so the name must be
    // identical across every environment or the app looks for a queue that doesn't exist there.
    public static final String QUEUE_NAME = "pcr.hearing-resulted";

    private final String adminConnectionString;
    private final String connectionString;
    private final boolean autoStartProcessors;
    private final int maxTries;

    public ServiceBusProperties(
            @Value("${service-bus.admin-connection}") final String adminConnectionString,
            @Value("${service-bus.connection}") final String connectionString,
            @Value("${service-bus.auto-start-processors}") final boolean autoStartProcessors,
            @Value("${service-bus.max-tries}") final int maxTries
    ) {
        log.info("ServiceBusProperties initialised queueName:{} autoStartProcessors:{} maxTries:{}",
                QUEUE_NAME, autoStartProcessors, maxTries);
        this.adminConnectionString = adminConnectionString;
        this.connectionString = connectionString;
        this.autoStartProcessors = autoStartProcessors;
        this.maxTries = maxTries;
    }

    public boolean isEmulator() {
        return !connectionString.contains(HTTPS);
    }
}
