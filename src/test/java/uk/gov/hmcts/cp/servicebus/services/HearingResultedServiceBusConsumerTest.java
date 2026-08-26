package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingResultedServiceBusConsumerTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final LocalDate HEARING_DAY = LocalDate.parse("2026-07-23");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock
    private ServiceBusProvisioningService provisioningService;
    @Mock
    private ServiceBusClientFactory clientFactory;
    @Mock
    private ServiceBusProperties properties;
    @Mock
    private ResultsIngestionService ingestionService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ServiceBusRetryService retryService;

    @Mock
    private ServiceBusReceivedMessageContext context;
    @Mock
    private ServiceBusReceivedMessage message;
    @Mock
    private ServiceBusSenderClient senderClient;
    @Mock
    private ServiceBusClientBuilder.ServiceBusProcessorClientBuilder processorClientBuilder;
    @Mock
    private ServiceBusProcessorClient processorClient;

    @Captor
    private ArgumentCaptor<ServiceBusMessage> messageCaptor;
    @Captor
    private ArgumentCaptor<DeadLetterOptions> deadLetterCaptor;

    @Spy
    @InjectMocks
    private HearingResultedServiceBusConsumer consumer;

    @Test
    void initialise_should_skipEntirely_whenAutoStartProcessorsAndIngestionBothDisabled() {
        when(properties.isAutoStartProcessors()).thenReturn(false);
        when(properties.isIngestionEnabled()).thenReturn(false);

        consumer.initialise();

        verify(provisioningService, never()).isServiceBusReady();
        verify(provisioningService, never()).queueExists(any());
        verify(clientFactory, never()).processorClientBuilder();
    }

    @Test
    void initialise_should_start_whenIngestionEnabled_evenIfAutoStartProcessorsDisabled() {
        when(properties.isAutoStartProcessors()).thenReturn(false);
        when(properties.isIngestionEnabled()).thenReturn(true);
        when(provisioningService.isServiceBusReady()).thenReturn(true);
        when(provisioningService.queueExists(ServiceBusProperties.QUEUE_NAME)).thenReturn(true);
        givenProcessorBuilder();

        consumer.initialise();

        verify(provisioningService).queueExists(ServiceBusProperties.QUEUE_NAME);
        verify(processorClient).start();
    }

    @Test
    void initialise_should_provisionAndStartProcessor_whenServiceBusReadyImmediately() {
        when(properties.isAutoStartProcessors()).thenReturn(true);
        when(provisioningService.isServiceBusReady()).thenReturn(true);
        when(provisioningService.queueExists(ServiceBusProperties.QUEUE_NAME)).thenReturn(true);
        givenProcessorBuilder();

        consumer.initialise();

        verify(provisioningService).queueExists(ServiceBusProperties.QUEUE_NAME);
        verify(processorClient).start();
    }

    @Test
    void initialise_should_pollUntilReady_beforeCheckingQueue() {
        when(properties.isAutoStartProcessors()).thenReturn(true);
        when(provisioningService.isServiceBusReady()).thenReturn(false, false, true);
        when(provisioningService.queueExists(any())).thenReturn(true);
        givenProcessorBuilder();

        consumer.initialise();

        verify(provisioningService, times(3)).isServiceBusReady();
        verify(provisioningService).queueExists(any());
        verify(processorClient).start();
    }

    @Test
    void initialise_should_propagateException_whenQueueExistsCheckFails() {
        when(properties.isAutoStartProcessors()).thenReturn(true);
        when(provisioningService.isServiceBusReady()).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(provisioningService).queueExists(any());

        assertThatThrownBy(() -> consumer.initialise())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(processorClient, never()).start();
    }

    @Test
    void initialise_should_throwIllegalStateException_whenQueueDoesNotExist() {
        when(properties.isAutoStartProcessors()).thenReturn(true);
        when(provisioningService.isServiceBusReady()).thenReturn(true);
        when(provisioningService.queueExists(ServiceBusProperties.QUEUE_NAME)).thenReturn(false);

        assertThatThrownBy(() -> consumer.initialise())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ServiceBusProperties.QUEUE_NAME)
                .hasMessageContaining("Terraform");

        verify(clientFactory, never()).processorClientBuilder();
        verify(processorClient, never()).start();
    }

    private void givenProcessorBuilder() {
        when(clientFactory.processorClientBuilder()).thenReturn(processorClientBuilder);
        when(processorClientBuilder.processMessage(any())).thenReturn(processorClientBuilder);
        when(processorClientBuilder.processError(any())).thenReturn(processorClientBuilder);
        when(processorClientBuilder.buildProcessorClient()).thenReturn(processorClient);
    }

    @Test
    void processMessage_should_ingestAndComplete_whenIngestionEnabledAndComplete() {
        when(properties.isIngestionEnabled()).thenReturn(true);
        givenMessage(hearingResultedEventJson(), null);

        consumer.processMessage(context);

        verify(ingestionService).ingestAndPersistOnce(HEARING_ID, HEARING_DAY);
        verify(context).complete();
        verify(context, never()).abandon();
        verify(context, never()).deadLetter(any());
    }

    @Test
    void processMessage_should_completeWithoutIngesting_whenSwitchOff() {
        when(properties.isIngestionEnabled()).thenReturn(false);
        givenMessage(hearingResultedEventJson(), null);

        consumer.processMessage(context);

        verify(ingestionService, never()).ingestAndPersistOnce(any(), any());
        verify(context).complete();
    }

    @Test
    void processMessage_should_abandon_whenEventTypeUnrecognized() {
        when(properties.isIngestionEnabled()).thenReturn(true);
        givenMessage(unrecognizedEventTypeJson(), null);

        consumer.processMessage(context);

        verify(ingestionService, never()).ingestAndPersistOnce(any(), any());
        verify(context).abandon();
        verify(context, never()).complete();
    }

    @Test
    void processMessage_should_abandon_whenIngestionThrowsUnexpectedException() {
        when(properties.isIngestionEnabled()).thenReturn(true);
        givenMessage(hearingResultedEventJson(), null);
        doThrow(new IllegalStateException("malformed cache payload"))
                .when(ingestionService).ingestAndPersistOnce(HEARING_ID, HEARING_DAY);

        consumer.processMessage(context);

        verify(context).abandon();
        verify(context, never()).complete();
    }

    @Test
    void processMessage_should_completeAndScheduleFollowUp_whenIncompleteAndAttemptsRemain() {
        when(properties.isIngestionEnabled()).thenReturn(true);
        givenMessage(hearingResultedEventJson(), 1);
        doThrow(new IncompleteHearingDetailsException(HEARING_ID))
                .when(ingestionService).ingestAndPersistOnce(HEARING_ID, HEARING_DAY);
        final OffsetDateTime nextTryTime = OffsetDateTime.parse("2026-07-28T10:00:02Z");
        when(retryService.getNextTryTime(1)).thenReturn(nextTryTime);
        when(clientFactory.senderClient()).thenReturn(senderClient);

        consumer.processMessage(context);

        verify(context).complete();
        verify(context, never()).deadLetter(any());
        verify(senderClient).sendMessage(messageCaptor.capture());
        final ServiceBusMessage followUp = messageCaptor.getValue();
        assertThat(followUp.getApplicationProperties().get("attempt")).isEqualTo(2);
        assertThat(followUp.getScheduledEnqueueTime()).isEqualTo(nextTryTime);
    }

    @Test
    void processMessage_should_deadLetter_whenIncompleteAndAttemptsExhausted() {
        when(properties.isIngestionEnabled()).thenReturn(true);
        givenMessage(hearingResultedEventJson(), ResultsIngestionService.MAX_COMPLETENESS_RETRIES);
        doThrow(new IncompleteHearingDetailsException(HEARING_ID))
                .when(ingestionService).ingestAndPersistOnce(HEARING_ID, HEARING_DAY);

        consumer.processMessage(context);

        verify(context, never()).complete();
        verify(context).deadLetter(deadLetterCaptor.capture());
        assertThat(deadLetterCaptor.getValue().getDeadLetterReason())
                .isEqualTo("IncompleteHearingDetailsException after 3 attempts");
        verify(clientFactory, never()).senderClient();
    }

    @Test
    void processError_should_notThrow() {
        final com.azure.messaging.servicebus.ServiceBusErrorContext errorContext =
                org.mockito.Mockito.mock(com.azure.messaging.servicebus.ServiceBusErrorContext.class);
        when(errorContext.getException()).thenReturn(new RuntimeException("amqp link closed"));

        consumer.processError(errorContext);
    }

    private void givenMessage(final String body, final Integer attempt) {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(body));
        when(message.getApplicationProperties())
                .thenReturn(attempt == null ? Map.of() : Map.of("attempt", attempt));
    }

    private String hearingResultedEventJson() {
        final HearingResultedEvent event = new HearingResultedEvent()
                .id("evt-1")
                .eventType("Hearing_Resulted")
                .data(new HearingResultedEventData()
                        .hearingId(HEARING_ID)
                        .hearingDay(HEARING_DAY)
                        .userId(USER_ID));
        return objectMapper.writeValueAsString(event);
    }

    private String unrecognizedEventTypeJson() {
        final HearingResultedEvent event = new HearingResultedEvent().id("evt-1").eventType("Some_Other_Event");
        return objectMapper.writeValueAsString(event);
    }
}
