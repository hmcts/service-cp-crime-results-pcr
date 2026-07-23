package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class ServiceBusProperties {

    private static final String HTTPS = "https";

    private final String adminConnection;
    private final String connection;
    private final String queueName;

    public ServiceBusProperties(
            @Value("${service-bus.admin-connection}") final String adminConnection,
            @Value("${service-bus.connection}") final String connection,
            @Value("${service-bus.queue-name}") final String queueName) {
        this.adminConnection = adminConnection;
        this.connection = connection;
        this.queueName = queueName;
    }

    public boolean isEmulator() {
        return !connection.contains(HTTPS);
    }
}