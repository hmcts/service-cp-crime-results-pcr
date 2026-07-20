package uk.gov.hmcts.cp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.CORRELATION_ID_KEY;

class TracingIntegrationTest extends IntegrationTestBase {

    private static final String TEST_CORRELATION_ID = "12345678-1234-1234-1234-123456789012";
    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
        stubHearingDetails();
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void request_with_correlation_id_header_should_echo_it_in_response() throws Exception {
        final MvcResult result = mockMvc.perform(getLatestPcrVersionRequest()
                        .header(CORRELATION_ID_KEY, TEST_CORRELATION_ID))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void request_without_correlation_id_header_should_generate_one_in_response() throws Exception {
        final MvcResult result = mockMvc.perform(getLatestPcrVersionRequest())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isNotBlank();
    }

    private MockHttpServletRequestBuilder getLatestPcrVersionRequest() {
        return get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}/versions/latest",
                CASE_URN, HEARING_ID, DEFENDANT_ID)
                .accept(MediaType.APPLICATION_JSON);
    }

    private void stubHearingDetails() {
        final String url = String.format("%s/%s", appProperties.getResultsQueryPath(), HEARING_ID);
        stubFor(WireMock.get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"hearing":{
                          "courtCentre":{"id":"6b16f870-9dcb-4b62-88a1-b6a5b6e8e6b1","code":"LND001","name":"Central London Crown Court"},
                          "hearingDays":[{"sittingDay":"2026-06-23"}],
                          "prosecutionCases":[{
                            "id":"99999999-9999-9999-9999-999999999999",
                            "prosecutionCaseIdentifier":{"caseURN":"%s"},
                            "caseMarkers":[],
                            "defendants":[{
                              "id":"%s",
                              "masterDefendantId":"33333333-3333-3333-3333-333333333333",
                              "personDefendant":{"personDetails":{"firstName":"John","lastName":"Doe"}},
                              "offences":[]
                            }]
                          }],
                          "courtApplications":[]
                        }}
                        """.formatted(CASE_URN, DEFENDANT_ID))));
    }
}