package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.http.rest.PagedIterable;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
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

    private static final String QUEUE_NAME = "pcr.hearing-resulted";

    @Mock
    private ServiceBusAdministrationClient adminClient;

    @Mock
    private PagedIterable<QueueProperties> queues;

    @Captor
    private ArgumentCaptor<CreateQueueOptions> optionsCaptor;

    @InjectMocks
    private ServiceBusProvisioningService provisioningService;

    @Test
    void isServiceBusReady_should_returnTrue_whenAdminClientResponds() {
        when(adminClient.listQueues()).thenReturn(queues);
        when(queues.stream()).thenReturn(Stream.empty());

        assertThat(provisioningService.isServiceBusReady()).isTrue();
    }

    @Test
    void isServiceBusReady_should_returnFalse_whenAdminClientThrows() {
        when(adminClient.listQueues()).thenThrow(new RuntimeException("unreachable"));

        assertThat(provisioningService.isServiceBusReady()).isFalse();
    }

    @Test
    void createQueueIfNotExists_should_skipCreate_whenQueueAlreadyExists() {
        when(adminClient.getQueueExists(QUEUE_NAME)).thenReturn(true);

        provisioningService.createQueueIfNotExists(QUEUE_NAME);

        verify(adminClient, never()).createQueue(eq(QUEUE_NAME), any());
    }

    @Test
    void createQueueIfNotExists_should_createWithDurableProperties_whenQueueDoesNotExist() {
        when(adminClient.getQueueExists(QUEUE_NAME)).thenReturn(false);

        provisioningService.createQueueIfNotExists(QUEUE_NAME);

        verify(adminClient).createQueue(eq(QUEUE_NAME), optionsCaptor.capture());
        final CreateQueueOptions options = optionsCaptor.getValue();
        assertThat(options.getLockDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(options.getMaxDeliveryCount()).isEqualTo(10);
        assertThat(options.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(options.isDeadLetteringOnMessageExpiration()).isTrue();
    }
}
