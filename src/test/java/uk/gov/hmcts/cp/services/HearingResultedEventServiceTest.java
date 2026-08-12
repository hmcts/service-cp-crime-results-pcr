package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEventData;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HearingResultedEventServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock
    private ResultsIngestionService ingestionService;

    @InjectMocks
    private HearingResultedEventService eventService;

    @Test
    void handle_should_ingestAndReturn200_whenHearingResultedEvent() {
        final HearingResultedEvent event = hearingResultedEvent();

        final ResponseEntity<Void> response = eventService.handle(List.of(event));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);
    }

    @Test
    void handle_should_propagateIncompleteHearingDetailsException_whenIngestionThrows() {
        final HearingResultedEvent event = hearingResultedEvent();
        doThrow(new IncompleteHearingDetailsException(HEARING_ID))
                .when(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);

        assertThatThrownBy(() -> eventService.handle(List.of(event)))
                .isInstanceOf(IncompleteHearingDetailsException.class);
    }

    @Test
    void handle_should_throwIllegalArgumentException_whenEventTypeUnrecognized() {
        final HearingResultedEvent event = new HearingResultedEvent()
                .id("evt-1").eventType("Some_Other_Event");

        assertThatThrownBy(() -> eventService.handle(List.of(event)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(ingestionService, never()).ingestAndPersist(any(), any());
    }

    private HearingResultedEvent hearingResultedEvent() {
        return new HearingResultedEvent()
                .id("evt-2")
                .eventType("Hearing_Resulted")
                .data(new HearingResultedEventData()
                        .hearingId(HEARING_ID)
                        .hearingDay(LocalDate.parse(HEARING_DAY))
                        .userId(USER_ID));
    }
}
