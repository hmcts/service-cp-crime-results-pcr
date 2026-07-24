package uk.gov.hmcts.cp.services;

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
import uk.gov.hmcts.cp.clients.HearingResultedCacheClient;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.clients.ResultsClient;
import uk.gov.hmcts.cp.config.RetryServiceConfig;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsIngestionServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";
    private static final HearingResultedPointer POINTER = new HearingResultedPointer(HEARING_ID, HEARING_DAY, "userId");

    @Mock
    private HearingResultedCacheClient cacheClient;
    @Mock
    private ResultsClient resultsClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private HearingResultedServiceBusClientFactory clientFactory;
    @Mock
    private ServiceBusReceivedMessageContext context;
    @Mock
    private ServiceBusReceivedMessage message;
    @Mock
    private ServiceBusSenderClient senderClient;
    @Spy
    private RetryServiceConfig retryServiceConfig =
            new RetryServiceConfig(List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2)), 3);

    @InjectMocks
    private ResultsIngestionService ingestionService;

    @Test
    void ingest_should_returnCachedPayload_whenRedisHit() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY))
                .thenReturn(Optional.of("{\"hearing\":{\"prosecutionCases\":[{\"id\":\"case-1\"}]}}"));

        final HearingDetailsResponse result = ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
        verify(resultsClient, never()).getHearingDetails(any(UUID.class));
    }

    @Test
    void ingest_should_throwIllegalStateException_whenCachedPayloadIsMalformed() {
        // No HTTP status here — this path never runs inside a request, only the Service
        // Bus consumer, which just treats it as another "genuinely wrong" dead-letter case.
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.of("not-json"));

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ingest_should_fetchViaRest_whenRedisMiss_andFirstResponseIsComplete() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(completeResponse());

        final HearingDetailsResponse result = ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
    }

    @Test
    void ingest_should_throwIncompleteHearingDetailsException_whenFirstResponseIsIncomplete() {
        // Single-tier retry, matching HRDS's shape: no in-process loop — one incomplete
        // response fails fast and hands off to escalateOrDeadLetter's Service Bus escalation.
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
    }

    @Test
    void escalateOrDeadLetter_should_completeMessageAndSendRetryMessage_whenUnderMaxRetries() {
        when(context.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(new HashMap<>());
        when(clientFactory.senderClient()).thenReturn(senderClient);

        ingestionService.escalateOrDeadLetter(context, POINTER);

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

        ingestionService.escalateOrDeadLetter(context, POINTER);

        verify(context).deadLetter();
        verify(context, never()).complete();
        verify(clientFactory, never()).senderClient();
    }

    private HearingDetailsResponse completeResponse() {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .prosecutionCases(List.of(HearingDetailsResponse.ProsecutionCase.builder().id("case-1").build()))
                        .build())
                .build();
    }

    private HearingDetailsResponse incompleteResponse() {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .prosecutionCases(List.of())
                        .build())
                .build();
    }
}