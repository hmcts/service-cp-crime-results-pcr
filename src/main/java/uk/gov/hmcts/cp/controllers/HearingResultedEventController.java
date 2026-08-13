package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.InternalApi;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.services.HearingResultedEventService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HearingResultedEventController implements InternalApi {

    private final HearingResultedEventService eventService;

    @Override
    public ResponseEntity<Void> receiveHearingResultedEvent(final List<HearingResultedEvent> hearingResultedEvent) {
        return eventService.handle(hearingResultedEvent);
    }
}

