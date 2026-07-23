package uk.gov.hmcts.cp.services;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsIngestionService {

    private static final String RETRY_COUNT_PROPERTY = "retryCount";

    private final HearingResultedCacheClient cacheClient;
    private final ResultsClient resultsClient;
    private final ObjectMapper objectMapper;
    private final HearingResultedServiceBusClientFactory clientFactory;
    private final RetryServiceConfig retryServiceConfig;

    public HearingDetailsResponse ingestHearingResults(final UUID hearingId, final String hearingDay) {
        return cacheClient.get(hearingId, hearingDay)
                .map(this::deserializeCachedHearingResults)
                .orElseGet(() -> getHearingResults(hearingId));
    }

    private HearingDetailsResponse deserializeCachedHearingResults(final String cachedJson) {
        try {
            return objectMapper.readValue(cachedJson, HearingDetailsResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Malformed cached hearing-result payload", e);
        }
    }

    private HearingDetailsResponse getHearingResults(final UUID hearingId) {
        final HearingDetailsResponse response = resultsClient.getHearingDetails(hearingId);
        if (isComplete(response)) {
            return response;
        }
        log.warn("Incomplete hearing details for hearingId:{} — viewstore may not have caught up yet", hearingId);
        throw new IncompleteHearingDetailsException(hearingId);
    }

    private boolean isComplete(final HearingDetailsResponse response) {
        return response != null
                && response.getHearing() != null
                && response.getHearing().getProsecutionCases() != null
                && !response.getHearing().getProsecutionCases().isEmpty();
    }

    public void escalateOrDeadLetter(final ServiceBusReceivedMessageContext context, final HearingResultedPointer hearingResultedPointer) {
        final int retryCount = retryCountOf(context.getMessage()) + 1;
        if (retryCount > retryServiceConfig.maxTries()) {
            log.error("Giving up on hearingId:{} after {} scheduled retries — dead-lettering explicitly",
                    hearingResultedPointer.hearingId(), retryCount);
            context.deadLetter();
            return;
        }
        context.complete();
        final Duration delay = retryServiceConfig.delayFor(retryCount);
        log.warn("Scheduling retry {}/{} for hearingId:{} in {}", retryCount, retryServiceConfig.maxTries(), hearingResultedPointer.hearingId(), delay);
        sendRetryMessage(hearingResultedPointer, retryCount, delay);
    }

    private void sendRetryMessage(final HearingResultedPointer pointer, final int retryCount, final Duration delay) {
        final ServiceBusMessage retryMessage = newRetryMessage(pointer, retryCount, delay);
        try (ServiceBusSenderClient sender = clientFactory.senderClient()) {
            sender.sendMessage(retryMessage);
        }
    }

    private ServiceBusMessage newRetryMessage(final HearingResultedPointer hearingResultedPointer, final int retryCount, final Duration delay) {
        final ServiceBusMessage message = new ServiceBusMessage(objectMapper.writeValueAsString(hearingResultedPointer));
        message.getApplicationProperties().put(RETRY_COUNT_PROPERTY, retryCount);
        message.setScheduledEnqueueTime(OffsetDateTime.now().plus(delay));
        return message;
    }

    private int retryCountOf(final ServiceBusReceivedMessage message) {
        final Object value = message.getApplicationProperties().get(RETRY_COUNT_PROPERTY);
        return value == null ? 0 : (int) value;
    }
}