package uk.gov.hmcts.cp.integration.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HearingResultedIngestionE2EIntegrationTest extends HearingResultedFixtureAssertions {

    @Transactional
    @Test
    void twoDefendantOneApplicationHearing_should_persistAndExposeViaGetPcr_whenPrisonCourtRegisterSubscriptionMatches() throws Exception {
        given_a_matching_prison_court_register_subscription();
        given_the_real_hearing_payload_is_seeded_in_redis();

        when_the_hearing_resulted_event_is_received();

        then_the_case_hearing_is_persisted();
        then_the_version_is_persisted_with_defendant_pii_and_custody();
        then_the_court_application_is_persisted();
        then_the_offence_and_judicial_result_are_persisted();
        then_the_judicial_result_prompts_are_persisted();
        then_the_get_pcr_query_returns_the_persisted_result();
    }

    private void when_the_hearing_resulted_event_is_received() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/hearing-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hearingResultedEvent()))
                .andExpect(status().isOk());
    }
}
