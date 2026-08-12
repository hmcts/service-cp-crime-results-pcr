package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.util.List;

// pcr-eventgrid-relay-function now absorbs Event Grid's subscription-validation handshake
// upstream (its own @EventGridTrigger binding answers it) and relays only real Hearing_Resulted
// events here via a plain internal HTTP call — this service no longer branches on eventType for
// a handshake it will never receive.
@Service
@RequiredArgsConstructor
@Slf4j
public class HearingResultedEventService {

    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";

    private final ResultsIngestionService ingestionService;

    public ResponseEntity<Void> handle(final List<HearingResultedEvent> events) {
        final HearingResultedEvent event = firstEvent(events);
        if (!HEARING_RESULTED_EVENT_TYPE.equals(event.getEventType())) {
            throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        }
        return ingest(event);
    }

    private HearingResultedEvent firstEvent(final List<HearingResultedEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Empty Event Grid delivery — expected at least one event");
        }
        return events.get(0);
    }

    private ResponseEntity<Void> ingest(final HearingResultedEvent event) {
        final HearingResultedEventData data = event.getData();
        ingestionService.ingestAndPersist(data.getHearingId(), data.getHearingDay().toString());
        return ResponseEntity.ok().build();
    }
}
