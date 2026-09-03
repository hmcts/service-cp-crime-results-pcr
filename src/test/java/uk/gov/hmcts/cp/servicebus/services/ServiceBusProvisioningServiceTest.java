package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.http.rest.PagedIterable;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBusProvisioningServiceTest {

    private static final String QUEUE_NAME = "pcr.hearing-resulted";

    @Mock
    private ServiceBusAdministrationClient adminClient;

    @Mock
    private PagedIterable<QueueProperties> queues;

    @Mock
    private QueueProperties queueProperties;

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
    void queueExists_should_returnTrue_whenAdminClientConfirms() {
        when(adminClient.getQueueExists(QUEUE_NAME)).thenReturn(true);

        assertThat(provisioningService.queueExists(QUEUE_NAME)).isTrue();
    }

    @Test
    void queueExists_should_returnFalse_whenAdminClientReportsMissing() {
        when(adminClient.getQueueExists(QUEUE_NAME)).thenReturn(false);

        assertThat(provisioningService.queueExists(QUEUE_NAME)).isFalse();
    }

    @Test
    void maxDeliveryCountOf_should_returnConfiguredValue() {
        when(adminClient.getQueue(QUEUE_NAME)).thenReturn(queueProperties);
        when(queueProperties.getMaxDeliveryCount()).thenReturn(10);

        assertThat(provisioningService.maxDeliveryCountOf(QUEUE_NAME)).isEqualTo(10);
    }
}
