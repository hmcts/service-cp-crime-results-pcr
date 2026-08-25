package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions;
import com.azure.messaging.servicebus.administration.models.TopicProperties;
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    // Best-effort readiness ping — any failure (auth, network, DNS) means "not ready yet", not a
    // specific handled case, matching HRDS's ServiceBusAdminService.isServiceBusReady precedent.
    public boolean isServiceBusReady() {
        boolean ready;
        try {
            final List<String> topics = adminClient.listTopics().stream().map(TopicProperties::getName).toList();
            log.info("ServiceBus has topics:{}", topics);
            ready = true;
        } catch (Exception e) {
            log.info("ServiceBus is not available. Error:{}", e.getMessage());
            ready = false;
        }
        return ready;
    }

    public void createTopicIfNotExists(final String topicName) {
        if (adminClient.getTopicExists(topicName)) {
            log.info("Topic {} already exists", topicName);
        } else {
            log.info("Creating topic {}", topicName);
            adminClient.createTopic(topicName);
        }
    }

    public void createSubscriptionIfNotExists(final String topicName, final String subscriptionName) {
        if (adminClient.getSubscriptionExists(topicName, subscriptionName)) {
            log.info("Subscription {} on topic {} already exists", subscriptionName, topicName);
            return;
        }
        log.info("Creating subscription {} on topic {}", subscriptionName, topicName);
        final CreateSubscriptionOptions options = new CreateSubscriptionOptions();
        options.setLockDuration(LOCK_DURATION);
        options.setMaxDeliveryCount(MAX_DELIVERY_COUNT);
        options.setDefaultMessageTimeToLive(DEFAULT_MESSAGE_TIME_TO_LIVE);
        options.setDeadLetteringOnMessageExpiration(true);
        adminClient.createSubscription(topicName, subscriptionName, options);
    }
}
