package uk.gov.hmcts.cp.services.ingestion;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.config.RetryServiceConfig;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class HearingResultedRetryService {

    private static final String RETRY_COUNT_PROPERTY = "retryCount";

    private final HearingResultedServiceBusClientFactory clientFactory;
    private final RetryServiceConfig retryServiceConfig;
    private final ObjectMapper objectMapper;

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