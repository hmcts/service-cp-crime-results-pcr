package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class ServiceBusProvisioningService {

    private static final Duration LOCK_DURATION = Duration.ofMinutes(1);
    private static final int MAX_DELIVERY_COUNT = 10;
    private static final Duration DEFAULT_MESSAGE_TIME_TO_LIVE = Duration.ofMinutes(10);

    private final ServiceBusAdministrationClient adminClient;

    public boolean isServiceBusReady() {
        boolean ready;
        try {
            final List<String> queues = adminClient.listQueues().stream().map(QueueProperties::getName).toList();
            log.info("ServiceBus has queues:{}", queues);
            ready = true;
        } catch (Exception e) {
            log.info("ServiceBus is not available. Error:{}", e.getMessage());
            ready = false;
        }
        return ready;
    }

    public void createQueueIfNotExists(final String queueName) {
        if (adminClient.getQueueExists(queueName)) {
            log.info("Queue {} already exists", queueName);
            return;
        }
        log.info("Creating queue {}", queueName);
        final CreateQueueOptions options = new CreateQueueOptions();
        options.setLockDuration(LOCK_DURATION);
        options.setMaxDeliveryCount(MAX_DELIVERY_COUNT);
        options.setDefaultMessageTimeToLive(DEFAULT_MESSAGE_TIME_TO_LIVE);
        options.setDeadLetteringOnMessageExpiration(true);
        adminClient.createQueue(queueName, options);
    }
}
