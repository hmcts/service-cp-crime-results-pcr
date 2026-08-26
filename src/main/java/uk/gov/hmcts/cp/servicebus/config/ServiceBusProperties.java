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

    // Fixed, not environment config — PCR provisions this queue itself (create-if-not-exists),
    // so the name must be identical across every environment or provisioning silently diverges.
    public static final String QUEUE_NAME = "pcr.hearing-resulted";

    private final String adminConnectionString;
    private final String connectionString;
    private final boolean ingestionEnabled;
    private final boolean autoStartProcessors;

    public ServiceBusProperties(
            @Value("${service-bus.admin-connection}") final String adminConnectionString,
            @Value("${service-bus.connection}") final String connectionString,
            @Value("${service-bus.ingestion-enabled}") final boolean ingestionEnabled,
            @Value("${service-bus.auto-start-processors}") final boolean autoStartProcessors
    ) {
        log.info("ServiceBusProperties initialised queueName:{} ingestionEnabled:{} autoStartProcessors:{}",
                QUEUE_NAME, ingestionEnabled, autoStartProcessors);
        this.adminConnectionString = adminConnectionString;
        this.connectionString = connectionString;
        this.ingestionEnabled = ingestionEnabled;
        this.autoStartProcessors = autoStartProcessors;
    }

    public boolean isEmulator() {
        return !connectionString.contains(HTTPS);
    }
}
