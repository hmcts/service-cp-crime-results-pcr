package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService.MAX_COMPLETENESS_RETRIES;

@Slf4j
@Service
@RequiredArgsConstructor
public class HearingResultedServiceBusConsumer {

    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";
    private static final String ATTEMPT_PROPERTY = "attempt";
    private static final String DEAD_LETTER_REASON = "IncompleteHearingDetailsException after "
            + MAX_COMPLETENESS_RETRIES + " attempts";
    private static final String MALFORMED_PAYLOAD_REASON = "Malformed HearingResultedEvent payload";
    private static final Duration MAX_READINESS_WAIT = Duration.ofMinutes(2);
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofSeconds(2);

    private final ServiceBusProvisioningService provisioningService;
    private final ServiceBusClientFactory clientFactory;
    private final ServiceBusProperties properties;
    private final ResultsIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final ServiceBusRetryService retryService;

    private ServiceBusProcessorClient processorClient;

    @PostConstruct
    public void initialise() {
        if (!properties.isAutoStartProcessors() && !properties.isIngestionEnabled()) {
            log.info("service-bus.auto-start-processors=false and ingestion-enabled=false — skipping Service Bus initialisation");
            return;
        }
        awaitServiceBusReady();
        provisioningService.createQueueIfNotExists(ServiceBusProperties.QUEUE_NAME);
        processorClient = clientFactory.processorClientBuilder()
                .processMessage(this::processMessage)
                .processError(this::processError)
                .buildProcessorClient();
        processorClient.start();
        log.info("HearingResultedServiceBusConsumer started on pcr queue:{} ingestionEnabled:{}",
                ServiceBusProperties.QUEUE_NAME, properties.isIngestionEnabled());
    }

    private void awaitServiceBusReady() {
        await().atMost(MAX_READINESS_WAIT)
                .pollInterval(READINESS_POLL_INTERVAL)
                .until(provisioningService::isServiceBusReady);
    }

    /* default */ void processMessage(final ServiceBusReceivedMessageContext context) {
        final ServiceBusReceivedMessage message = context.getMessage();
        final int attempt = attemptOf(message);
        try {
            handle(context, message, attempt);
        } catch (IncompleteHearingDetailsException e) {
            handleIncomplete(context, message, attempt);
        } catch (Exception e) {
            log.error("processMessage unexpected error on attempt {} — abandoning for native redelivery. {}",
                    attempt, e.getMessage(), e);
            context.abandon();
        }
    }

    private void handle(final ServiceBusReceivedMessageContext context, final ServiceBusReceivedMessage message,
                         final int attempt) {
        final Optional<HearingResultedEvent> event = deserialize(context, message, attempt);
        if (event.isEmpty()) {
            return;
        }
        processEvent(event.get(), context, attempt);
    }

    private Optional<HearingResultedEvent> deserialize(final ServiceBusReceivedMessageContext context,
                                                         final ServiceBusReceivedMessage message, final int attempt) {
        HearingResultedEvent event = null;
        try {
            event = objectMapper.readValue(message.getBody().toString(), HearingResultedEvent.class);
        } catch (JacksonException e) {
            log.error("handle malformed message body on attempt {} — dead-lettering, not redelivering. {}",
                    attempt, e.getMessage());
            context.deadLetter(new DeadLetterOptions().setDeadLetterReason(MALFORMED_PAYLOAD_REASON));
        }
        return Optional.ofNullable(event);
    }

    private void processEvent(final HearingResultedEvent event, final ServiceBusReceivedMessageContext context,
                               final int attempt) {
        final HearingResultedEventData data = event.getData();
        log.info("HearingResultedServiceBusConsumer received channel:servicebus active:{} attempt:{} "
                        + "hearingId:{} hearingDay:{} userId:{}",
                properties.isIngestionEnabled(), attempt, data.getHearingId(), data.getHearingDay(), data.getUserId());
        if (!properties.isIngestionEnabled()) {
            context.complete();
            return;
        }
        if (!HEARING_RESULTED_EVENT_TYPE.equals(event.getEventType())) {
            throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        }
        ingestionService.ingestAndPersistOnce(data.getHearingId(), data.getHearingDay());
        context.complete();
    }

    private void handleIncomplete(final ServiceBusReceivedMessageContext context, final ServiceBusReceivedMessage message,
                                   final int attempt) {
        if (attempt >= MAX_COMPLETENESS_RETRIES) {
            log.warn("handleIncomplete exhausted after {} attempts — dead-lettering", attempt);
            context.deadLetter(new DeadLetterOptions().setDeadLetterReason(DEAD_LETTER_REASON));
            return;
        }
        context.complete();
        scheduleFollowUp(message, attempt);
    }

    private void scheduleFollowUp(final ServiceBusReceivedMessage message, final int attempt) {
        final ServiceBusMessage followUp = new ServiceBusMessage(BinaryData.fromBytes(message.getBody().toBytes()));
        followUp.getApplicationProperties().put(ATTEMPT_PROPERTY, attempt + 1);
        final OffsetDateTime nextTryTime = retryService.getNextTryTime(attempt);
        followUp.setScheduledEnqueueTime(nextTryTime);
        try (ServiceBusSenderClient sender = clientFactory.senderClient()) {
            sender.sendMessage(followUp);
        }
        log.info("scheduleFollowUp sent attempt:{} nextTryTime:{}", attempt + 1, nextTryTime);
    }

    private int attemptOf(final ServiceBusReceivedMessage message) {
        final Object value = message.getApplicationProperties().get(ATTEMPT_PROPERTY);
        return value == null ? 1 : (Integer) value;
    }

    /* default */ void processError(final ServiceBusErrorContext errorContext) {
        log.error("processError unexpected error on pcr queue:{}", ServiceBusProperties.QUEUE_NAME, errorContext.getException());
    }
}
