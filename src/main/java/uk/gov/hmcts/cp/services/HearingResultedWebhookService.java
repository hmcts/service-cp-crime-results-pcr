package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEventData;
import uk.gov.hmcts.cp.openapi.model.WebhookAck;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HearingResultedWebhookService {

    private static final String VALIDATION_EVENT_TYPE = "Microsoft.EventGrid.SubscriptionValidationEvent";
    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";

    private final ResultsIngestionService ingestionService;

    public ResponseEntity<WebhookAck> handle(final List<HearingResultedWebhookEvent> events) {
        final HearingResultedWebhookEvent event = firstEvent(events);
        return switch (event.getEventType()) {
            case VALIDATION_EVENT_TYPE -> echoValidation(event);
            case HEARING_RESULTED_EVENT_TYPE -> ingest(event);
            default -> throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        };
    }

    private HearingResultedWebhookEvent firstEvent(final List<HearingResultedWebhookEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Empty Event Grid delivery — expected at least one event");
        }
        return events.get(0);
    }

    private ResponseEntity<WebhookAck> echoValidation(final HearingResultedWebhookEvent event) {
        log.info("Echoing Event Grid subscription validation handshake");
        return ResponseEntity.ok(new WebhookAck().validationResponse(event.getData().getValidationCode()));
    }

    private ResponseEntity<WebhookAck> ingest(final HearingResultedWebhookEvent event) {
        final HearingResultedWebhookEventData data = event.getData();
        ingestionService.ingestAndPersist(data.getHearingId(), data.getHearingDay().toString());
        return ResponseEntity.ok(new WebhookAck());
    }
}
