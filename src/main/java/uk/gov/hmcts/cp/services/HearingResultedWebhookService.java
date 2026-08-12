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

// pcr-eventgrid-relay-function now absorbs Event Grid's subscription-validation handshake
// upstream (its own @EventGridTrigger binding answers it) and relays only real Hearing_Resulted
// events here via a plain internal HTTP call — this service no longer branches on eventType for
// a handshake it will never receive.
@Service
@RequiredArgsConstructor
@Slf4j
public class HearingResultedWebhookService {

    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";

    private final ResultsIngestionService ingestionService;

    public ResponseEntity<WebhookAck> handle(final List<HearingResultedWebhookEvent> events) {
        final HearingResultedWebhookEvent event = firstEvent(events);
        if (!HEARING_RESULTED_EVENT_TYPE.equals(event.getEventType())) {
            throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        }
        return ingest(event);
    }

    private HearingResultedWebhookEvent firstEvent(final List<HearingResultedWebhookEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Empty Event Grid delivery — expected at least one event");
        }
        return events.get(0);
    }

    private ResponseEntity<WebhookAck> ingest(final HearingResultedWebhookEvent event) {
        final HearingResultedWebhookEventData data = event.getData();
        ingestionService.ingestAndPersist(data.getHearingId(), data.getHearingDay().toString());
        return ResponseEntity.ok(new WebhookAck());
    }
}
