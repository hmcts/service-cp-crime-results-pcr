package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.services.ResultsIngestionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingResultedProcessorServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";
    private static final String ENVELOPE_JSON = """
            {
              "eventType": "Hearing_Resulted",
              "subject": "hearing/00000000-0000-0000-0000-000000000011",
              "eventTime": "2026-07-23T10:00:00Z",
              "data": {
                "hearingId": "00000000-0000-0000-0000-000000000011",
                "hearingDay": "2026-07-23",
                "userId": "00000000-0000-0000-0000-000000000099"
              }
            }
            """;

    @Mock
    private HearingResultedServiceBusClientFactory clientFactory;
    @Mock
    private ResultsIngestionService ingestionService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ServiceBusReceivedMessageContext context;
    @Mock
    private ServiceBusReceivedMessage message;

    @InjectMocks
    private HearingResultedProcessorService processorService;

    @Test
    void onMessage_should_unwrapEnvelopeAndCompleteMessage_whenIngestSucceeds() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));

        processorService.onMessage(context);

        verify(ingestionService).ingestHearingResults(HEARING_ID, HEARING_DAY);
        verify(context).complete();
    }

    @Test
    void onMessage_should_delegateToScheduleRetry_whenIngestThrowsIncompleteHearingDetailsException() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));
        when(ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .thenThrow(new IncompleteHearingDetailsException(HEARING_ID));

        processorService.onMessage(context);

        verify(ingestionService).escalateOrDeadLetter(eq(context), any(HearingResultedPointer.class));
        verify(context, never()).complete();
        verify(context, never()).deadLetter();
    }

    @Test
    void onMessage_should_deadLetter_whenIngestThrowsUnexpectedException() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));
        when(ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY)).thenThrow(new RuntimeException("boom"));

        processorService.onMessage(context);

        verify(context).deadLetter();
        verify(context, never()).complete();
    }

    @Test
    void onMessage_should_deadLetter_whenEnvelopeIsMalformed() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString("not-json"));

        processorService.onMessage(context);

        verify(context).deadLetter();
        verify(context, never()).complete();
        verify(ingestionService, never()).ingestHearingResults(any(), any());
    }

    @Test
    void start_should_buildAndStartProcessorClient() {
        final ServiceBusProcessorClient processorClient = org.mockito.Mockito.mock(ServiceBusProcessorClient.class);
        when(clientFactory.processorClient(any(), any())).thenReturn(processorClient);

        processorService.start();

        verify(processorClient).start();
    }

    @Test
    void start_should_notPropagate_whenClientFactoryUnreachable() {
        when(clientFactory.processorClient(any(), any())).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(processorService::start).doesNotThrowAnyException();
    }
}