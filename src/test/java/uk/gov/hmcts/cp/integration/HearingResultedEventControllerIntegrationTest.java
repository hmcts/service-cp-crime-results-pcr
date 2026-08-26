package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HearingResultedEventControllerIntegrationTest extends IntegrationTestBase {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void receiveHearingResultedEvent_should_ingestAndReturn200_whenHearingResultedEvent() throws Exception {
        final String body = """
                [{
                  "id": "evt-2",
                  "eventType": "Hearing_Resulted",
                  "data": { "hearingId": "00000000-0000-0000-0000-000000000011", "hearingDay": "2026-07-23", "userId": "00000000-0000-0000-0000-000000000099" }
                }]
                """;

        mockMvc.perform(post("/internal/hearing-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(resultsIngestionService).ingestAndPersist(eq(HEARING_ID), eq(LocalDate.parse("2026-07-23")));
    }

    @Test
    void receiveHearingResultedEvent_should_return400_whenEventTypeUnrecognized() throws Exception {
        final String body = """
                [{ "id": "evt-3", "eventType": "Some_Other_Event", "data": {} }]
                """;

        mockMvc.perform(post("/internal/hearing-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
