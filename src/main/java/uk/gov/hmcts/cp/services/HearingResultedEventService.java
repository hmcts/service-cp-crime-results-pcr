package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HearingResultedEventService {

    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";

    private final ResultsIngestionService ingestionService;

    public ResponseEntity<Void> handle(final List<HearingResultedEvent> events) {
        final HearingResultedEvent event = firstEvent(events);
        if (!HEARING_RESULTED_EVENT_TYPE.equals(event.getEventType())) {
            throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        }
        final HearingResultedEventData data = event.getData();
        ingestionService.ingestAndPersist(data.getHearingId(), data.getHearingDay().toString());
        return ResponseEntity.ok().build();
    }

    private HearingResultedEvent firstEvent(final List<HearingResultedEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Empty Event Grid delivery — expected at least one event");
        }
        return events.get(0);
    }
}
