package uk.gov.hmcts.cp.clients;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.config.ServiceBusProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingResultedQueueProvisionerTest {

    private static final String QUEUE_NAME = "pcr.hearing-resulted";

    @Mock
    private ServiceBusAdministrationClient adminClient;
    @Mock
    private ServiceBusProperties properties;

    @InjectMocks
    private HearingResultedQueueProvisioner provisioner;

    @Test
    void provisionQueue_should_createQueue_whenNotAlreadyPresent() {
        when(properties.getQueueName()).thenReturn(QUEUE_NAME);
        when(adminClient.getQueueExists(QUEUE_NAME)).thenReturn(false);

        provisioner.provisionQueue();

        verify(adminClient).createQueue(QUEUE_NAME);
    }

    @Test
    void provisionQueue_should_doNothing_whenAlreadyPresent() {
        when(properties.getQueueName()).thenReturn(QUEUE_NAME);
        when(adminClient.getQueueExists(QUEUE_NAME)).thenReturn(true);

        provisioner.provisionQueue();

        verify(adminClient, never()).createQueue(any());
    }

    @Test
    void provisionQueue_should_notPropagate_whenAdminClientUnreachable() {
        when(properties.getQueueName()).thenReturn(QUEUE_NAME);
        when(adminClient.getQueueExists(QUEUE_NAME)).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(provisioner::provisionQueue).doesNotThrowAnyException();
    }
}