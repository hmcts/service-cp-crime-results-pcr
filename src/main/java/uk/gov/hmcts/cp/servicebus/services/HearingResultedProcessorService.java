package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.servicebus.model.EventGridData;
import uk.gov.hmcts.cp.servicebus.model.EventGridEnvelope;
import uk.gov.hmcts.cp.services.ResultsIngestionService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HearingResultedProcessorService {

    private final HearingResultedServiceBusClientFactory clientFactory;
    private final ResultsIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    private ServiceBusProcessorClient processorClient;

    @PostConstruct
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    /* default */ void start() {
        try {
            processorClient = clientFactory.processorClient(this::onMessage, this::onError);
            processorClient.start();
        } catch (Exception e) {
            log.error("Failed to start Service Bus processor — no Hearing_Resulted events will be consumed until this is resolved", e);
        }
    }

    /* default */ void onMessage(final ServiceBusReceivedMessageContext context) {
        unwrapOrDeadLetter(context).ifPresent(hearingResultedPointer -> processIngestion(context, hearingResultedPointer));
    }

    private Optional<HearingResultedPointer> unwrapOrDeadLetter(final ServiceBusReceivedMessageContext context) {
        Optional<HearingResultedPointer> hearingResultedPointer;
        try {
            hearingResultedPointer = Optional.of(toPointer(context.getMessage()));
        } catch (JacksonException | IllegalArgumentException e) {
            log.error("Malformed Hearing_Resulted message — dead-lettering", e);
            context.deadLetter();
            hearingResultedPointer = Optional.empty();
        }
        return hearingResultedPointer;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void processIngestion(final ServiceBusReceivedMessageContext context, final HearingResultedPointer hearingResultedPointer) {
        try {
            ingestionService.ingestHearingResults(hearingResultedPointer.hearingId(), hearingResultedPointer.hearingDay());
            context.complete();
        } catch (IncompleteHearingDetailsException _) {
            ingestionService.escalateOrDeadLetter(context, hearingResultedPointer);
        } catch (Exception e) {
            log.error("Unrecoverable failure ingesting hearingId:{}", hearingResultedPointer.hearingId(), e);
            context.deadLetter();
        }
    }

    private void onError(final ServiceBusErrorContext errorContext) {
        log.error("Unexpected Service Bus processor error", errorContext.getException());
    }

    private HearingResultedPointer toPointer(final ServiceBusReceivedMessage message) {
        final EventGridEnvelope envelope = objectMapper.readValue(message.getBody().toString(), EventGridEnvelope.class);
        final EventGridData data = envelope.data();
        if (data == null) {
            throw new IllegalArgumentException("Event Grid envelope missing 'data'");
        }
        return new HearingResultedPointer(data.hearingId(), data.hearingDay(), data.userId());
    }
}