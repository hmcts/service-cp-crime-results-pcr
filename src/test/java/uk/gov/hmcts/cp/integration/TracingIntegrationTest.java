package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.CORRELATION_ID_KEY;

class TracingIntegrationTest extends IntegrationTestBase {

    private static final String TEST_CORRELATION_ID = "12345678-1234-1234-1234-123456789012";
    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Test
    void request_with_correlation_id_header_should_echo_it_in_response() throws Exception {
        when(resultsPcrService.getPcrHearingResults(any(), any(), any())).thenReturn(List.of());

        final MvcResult result = mockMvc.perform(getPcrHearingResultsRequest()
                        .header(CORRELATION_ID_KEY, TEST_CORRELATION_ID))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void request_without_correlation_id_header_should_generate_one_in_response() throws Exception {
        when(resultsPcrService.getPcrHearingResults(any(), any(), any())).thenReturn(List.of());

        final MvcResult result = mockMvc.perform(getPcrHearingResultsRequest())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isNotBlank();
    }

    private MockHttpServletRequestBuilder getPcrHearingResultsRequest() {
        return get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                CASE_URN, HEARING_ID, DEFENDANT_ID)
                .accept(MediaType.APPLICATION_JSON);
    }
}