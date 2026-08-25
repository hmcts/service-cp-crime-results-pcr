package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.http.rest.PagedIterable;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions;
import com.azure.messaging.servicebus.administration.models.TopicProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBusProvisioningServiceTest {

    private static final String TOPIC_NAME = "hearing-resulted";
    private static final String SUBSCRIPTION_NAME = "pcr";

    @Mock
    private ServiceBusAdministrationClient adminClient;

    @Mock
    private PagedIterable<TopicProperties> topics;

    @Captor
    private ArgumentCaptor<CreateSubscriptionOptions> optionsCaptor;

    @InjectMocks
    private ServiceBusProvisioningService provisioningService;

    @Test
    void isServiceBusReady_should_returnTrue_whenAdminClientResponds() {
        when(adminClient.listTopics()).thenReturn(topics);
        when(topics.stream()).thenReturn(Stream.empty());

        assertThat(provisioningService.isServiceBusReady()).isTrue();
    }

    @Test
    void isServiceBusReady_should_returnFalse_whenAdminClientThrows() {
        when(adminClient.listTopics()).thenThrow(new RuntimeException("unreachable"));

        assertThat(provisioningService.isServiceBusReady()).isFalse();
    }

    @Test
    void createTopicIfNotExists_should_skipCreate_whenTopicAlreadyExists() {
        when(adminClient.getTopicExists(TOPIC_NAME)).thenReturn(true);

        provisioningService.createTopicIfNotExists(TOPIC_NAME);

        verify(adminClient, never()).createTopic(TOPIC_NAME);
    }

    @Test
    void createTopicIfNotExists_should_create_whenTopicDoesNotExist() {
        when(adminClient.getTopicExists(TOPIC_NAME)).thenReturn(false);

        provisioningService.createTopicIfNotExists(TOPIC_NAME);

        verify(adminClient).createTopic(TOPIC_NAME);
    }

    @Test
    void createSubscriptionIfNotExists_should_skipCreate_whenSubscriptionAlreadyExists() {
        when(adminClient.getSubscriptionExists(TOPIC_NAME, SUBSCRIPTION_NAME)).thenReturn(true);

        provisioningService.createSubscriptionIfNotExists(TOPIC_NAME, SUBSCRIPTION_NAME);

        verify(adminClient, never()).createSubscription(any(), any(), any());
    }

    @Test
    void createSubscriptionIfNotExists_should_createWithDurableProperties_whenSubscriptionDoesNotExist() {
        when(adminClient.getSubscriptionExists(TOPIC_NAME, SUBSCRIPTION_NAME)).thenReturn(false);

        provisioningService.createSubscriptionIfNotExists(TOPIC_NAME, SUBSCRIPTION_NAME);

        verify(adminClient).createSubscription(eq(TOPIC_NAME), eq(SUBSCRIPTION_NAME), optionsCaptor.capture());
        final CreateSubscriptionOptions options = optionsCaptor.getValue();
        assertThat(options.getLockDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(options.getMaxDeliveryCount()).isEqualTo(10);
        assertThat(options.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(options.isDeadLetteringOnMessageExpiration()).isTrue();
    }
}
