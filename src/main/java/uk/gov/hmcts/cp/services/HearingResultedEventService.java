package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HearingResultedEventService {

    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";

    private final ResultsIngestionService ingestionService;

    @Value("${service-bus.ingestion-enabled}")
    private boolean serviceBusIngestionEnabled;

    public ResponseEntity<Void> handle(final List<HearingResultedEvent> events) {
        validateNotEmpty(events);
        for (final HearingResultedEvent event : events) {
            ingestEvent(event);
        }
        return ResponseEntity.ok().build();
    }

    private void ingestEvent(final HearingResultedEvent event) {
        if (!HEARING_RESULTED_EVENT_TYPE.equals(event.getEventType())) {
            throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        }
        final HearingResultedEventData data = event.getData();
        log.info("HearingResultedEventService received channel:webhook active:{} hearingId:{} hearingDay:{} userId:{}",
                !serviceBusIngestionEnabled, data.getHearingId(), data.getHearingDay(), data.getUserId());
        if (serviceBusIngestionEnabled) {
            return;
        }
        ingestionService.ingestAndPersist(data.getHearingId(), data.getHearingDay().toString());
    }

    private void validateNotEmpty(final List<HearingResultedEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Empty Event Grid delivery — expected at least one event");
        }
    }
}
