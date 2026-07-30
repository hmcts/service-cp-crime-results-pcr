package uk.gov.hmcts.cp.services.ingestion;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.config.RetryServiceConfig;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingResultedRetryServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";
    private static final HearingResultedPointer POINTER = new HearingResultedPointer(HEARING_ID, HEARING_DAY, "userId");

    @Mock
    private HearingResultedServiceBusClientFactory clientFactory;
    @Spy
    private RetryServiceConfig retryServiceConfig =
            new RetryServiceConfig(List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2)), 3);
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ServiceBusReceivedMessageContext context;
    @Mock
    private ServiceBusReceivedMessage message;
    @Mock
    private ServiceBusSenderClient senderClient;

    @InjectMocks
    private HearingResultedRetryService retryService;

    @Test
    void escalateOrDeadLetter_should_completeMessageAndSendRetryMessage_whenUnderMaxRetries() {
        when(context.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(new HashMap<>());
        when(clientFactory.senderClient()).thenReturn(senderClient);

        retryService.escalateOrDeadLetter(context, POINTER);

        verify(context).complete();
        verify(context, never()).deadLetter();
        final ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(senderClient).sendMessage(captor.capture());
        final ServiceBusMessage sent = captor.getValue();
        assertThat(sent.getApplicationProperties()).containsEntry("retryCount", 1);
        assertThat(sent.getScheduledEnqueueTime()).isAfter(OffsetDateTime.now().plusSeconds(25));
    }

    @Test
    void escalateOrDeadLetter_should_deadLetter_whenMaxScheduledRetriesExceeded() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("retryCount", 3);
        when(context.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(properties);

        retryService.escalateOrDeadLetter(context, POINTER);

        verify(context).deadLetter();
        verify(context, never()).complete();
        verify(clientFactory, never()).senderClient();
    }
}