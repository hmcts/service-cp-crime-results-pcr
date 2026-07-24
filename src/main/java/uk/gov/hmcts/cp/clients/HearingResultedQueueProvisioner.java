package uk.gov.hmcts.cp.clients;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.ServiceBusProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class HearingResultedQueueProvisioner {

    private final ServiceBusAdministrationClient adminClient;
    private final ServiceBusProperties properties;

    @PostConstruct
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    /* default */ void provisionQueue() {
        final String queueName = properties.getQueueName();
        try {
            if (adminClient.getQueueExists(queueName)) {
                return;
            }
            log.info("Creating Service Bus queue {}", queueName);
            adminClient.createQueue(queueName);
        } catch (Exception e) {
            log.error("Failed to provision Service Bus queue {} — hearing event ingestion will not " +
                    "receive events until this is resolved", queueName, e);
        }
    }
}
