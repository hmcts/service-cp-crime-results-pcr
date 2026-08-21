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
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.services.ClockService;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.time.Duration;

import static uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService.MAX_COMPLETENESS_RETRIES;

/**
 * Consumes the shared {@code hearing-resulted} Service Bus topic via this service's own {@code pcr}
 * subscription (ADR-009/AMP-1030) — the replacement ingestion trigger for the Event Grid webhook
 * relay function. Always receives and logs, so both channels' receipt can be verified from logs
 * during the coexistence window; only persists when {@code service-bus.ingestion-enabled} is on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HearingResultedServiceBusConsumer {

    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";
    private static final String ATTEMPT_PROPERTY = "attempt";
    private static final String DEAD_LETTER_REASON = "IncompleteHearingDetailsException after "
            + MAX_COMPLETENESS_RETRIES + " attempts";
    // Matches HRDS's own emulator-startup allowance (its docker-compose healthcheck notes the
    // emulator can take up to 60s behind sqledge) — poll rather than fail fast on a cold stack.
    private static final int MAX_READINESS_WAIT_ATTEMPTS = 60;
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofSeconds(2);

    private final ServiceBusProvisioningService provisioningService;
    private final ServiceBusClientFactory clientFactory;
    private final ServiceBusProperties properties;
    private final ResultsIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final ClockService clockService;

    private ServiceBusProcessorClient processorClient;

    @PostConstruct
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    // Startup guard, matching HRDS's ServiceBusProcessorService.initialiseServiceBus precedent —
    // any provisioning/wiring failure is logged, not fatal; the app still starts without ingestion.
    public void initialise() {
        if (!properties.isAutoStartProcessors()) {
            log.info("service-bus.auto-start-processors=false — skipping Service Bus initialisation");
            return;
        }
        try {
            awaitServiceBusReady();
            provisioningService.createTopicIfNotExists(properties.getTopicName());
            provisioningService.createSubscriptionIfNotExists(properties.getTopicName(), properties.getSubscriptionName());
            processorClient = clientFactory.processorClientBuilder()
                    .processMessage(this::processMessage)
                    .processError(this::processError)
                    .buildProcessorClient();
            processorClient.start();
            log.info("HearingResultedServiceBusConsumer started on topic:{} subscription:{} ingestionEnabled:{}",
                    properties.getTopicName(), properties.getSubscriptionName(), properties.isIngestionEnabled());
        } catch (Exception e) {
            log.error("Failed to initialise HearingResultedServiceBusConsumer. {}", e.getMessage());
        }
    }

    private void awaitServiceBusReady() {
        int attempt = 0;
        while (!provisioningService.isServiceBusReady() && attempt < MAX_READINESS_WAIT_ATTEMPTS) {
            sleepUninterruptibly(READINESS_POLL_INTERVAL);
            attempt++;
        }
    }

    /* default */ void sleepUninterruptibly(final Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    // The outright-failure tier (design doc §"Retry mechanism") is deliberately unbounded — any
    // unexpected exception abandons the message for native Service Bus redelivery/dead-lettering.
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
        final HearingResultedEvent event = objectMapper.readValue(message.getBody().toString(), HearingResultedEvent.class);
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
        ingestionService.ingestAndPersistOnce(data.getHearingId(), data.getHearingDay().toString());
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
        followUp.setScheduledEnqueueTime(clockService.nowOffsetUTC().plus(ingestionService.backoffFor(attempt)));
        try (ServiceBusSenderClient sender = clientFactory.senderClient()) {
            sender.sendMessage(followUp);
        }
        log.info("scheduleFollowUp sent attempt:{} delay:{}", attempt + 1, ingestionService.backoffFor(attempt));
    }

    private int attemptOf(final ServiceBusReceivedMessage message) {
        final Object value = message.getApplicationProperties().get(ATTEMPT_PROPERTY);
        return value == null ? 1 : (Integer) value;
    }

    /* default */ void processError(final ServiceBusErrorContext errorContext) {
        log.error("processError unexpected error on subscription:{}", properties.getSubscriptionName(), errorContext.getException());
    }
}
