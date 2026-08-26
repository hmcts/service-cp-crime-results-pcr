package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class ServiceBusProvisioningService {

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

    public boolean queueExists(final String queueName) {
        return adminClient.getQueueExists(queueName);
    }
}
