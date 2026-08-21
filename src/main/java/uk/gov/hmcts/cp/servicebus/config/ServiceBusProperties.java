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

    private final String adminConnectionString;
    private final String connectionString;
    private final String topicName;
    private final String subscriptionName;
    private final boolean ingestionEnabled;
    private final boolean autoStartProcessors;

    public ServiceBusProperties(
            @Value("${service-bus.admin-connection}") final String adminConnectionString,
            @Value("${service-bus.connection}") final String connectionString,
            @Value("${service-bus.topic-name}") final String topicName,
            @Value("${service-bus.subscription-name}") final String subscriptionName,
            @Value("${service-bus.ingestion-enabled}") final boolean ingestionEnabled,
            @Value("${service-bus.auto-start-processors}") final boolean autoStartProcessors
    ) {
        log.info("ServiceBusProperties initialised topicName:{} subscriptionName:{} ingestionEnabled:{} autoStartProcessors:{}",
                topicName, subscriptionName, ingestionEnabled, autoStartProcessors);
        this.adminConnectionString = adminConnectionString;
        this.connectionString = connectionString;
        this.topicName = topicName;
        this.subscriptionName = subscriptionName;
        this.ingestionEnabled = ingestionEnabled;
        this.autoStartProcessors = autoStartProcessors;
    }

    public boolean isEmulator() {
        return !connectionString.contains(HTTPS);
    }
}
