package uk.gov.hmcts.cp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
class PcrControllerIntegrationTest extends IntegrationTestBase {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final UUID UNKNOWN_DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String UNKNOWN_CASE_URN = "ZZZZ9999999";

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void getLatestPcrVersion_should_returnOk_withMappedFields() throws Exception {
        stubHearingDetails(HTTP_OK, readResourceContents("pcr/hearing-details-full.json")
                .formatted(CASE_URN, DEFENDANT_ID, MASTER_DEFENDANT_ID));

        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}/versions/latest",
                        CASE_URN, HEARING_ID, DEFENDANT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.prosecutionCase.caseURN").value(CASE_URN))
                .andExpect(jsonPath("$.defendant.firstName").value("John"))
                .andExpect(jsonPath("$.custodyLocation").value("HMP Dovegate"))
                .andExpect(jsonPath("$.caseMarkers[0].code").value("DomesticViolence"))
                .andExpect(jsonPath("$.offences[0].code").value("TH68001"))
                .andExpect(jsonPath("$.offences[0].results[0].resultCode").value("1200"))
                .andExpect(jsonPath("$.offences[0].results[0].convicted").value("Y"))
                .andExpect(jsonPath("$.offences[0].results[0].financial").value("N"))
                .andExpect(jsonPath("$.offences[0].results[0].concurrent").value(true))
                .andExpect(jsonPath("$.offences[0].results[0].imprisonmentPeriod").value("6 Months 1 week"));
    }

    @Test
    void getLatestPcrVersion_should_return404_whenCaseUrnNotFound() throws Exception {
        stubHearingDetails(HTTP_OK, readResourceContents("pcr/hearing-details-no-cases.json"));

        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}/versions/latest",
                        UNKNOWN_CASE_URN, HEARING_ID, DEFENDANT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void getLatestPcrVersion_should_return404_whenDefendantIdNotFound() throws Exception {
        stubHearingDetails(HTTP_OK, readResourceContents("pcr/hearing-details-no-defendants.json")
                .formatted(CASE_URN));

        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}/versions/latest",
                        CASE_URN, HEARING_ID, UNKNOWN_DEFENDANT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void getLatestPcrVersion_should_return404_whenCpBackendReturns404() throws Exception {
        stubHearingDetails(HTTP_NOT_FOUND, "");

        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}/versions/latest",
                        CASE_URN, HEARING_ID, DEFENDANT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void getPcrVersionHistory_should_return501_notImplemented() throws Exception {
        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        CASE_URN, HEARING_ID, DEFENDANT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getPcrVersion_should_return501_notImplemented() throws Exception {
        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}/versions/{id}",
                        CASE_URN, HEARING_ID, DEFENDANT_ID, "01hxjk8v3xj0e5jz2h1p4c6q7r")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotImplemented());
    }

    private void stubHearingDetails(final int status, final String body) {
        final String url = String.format("%s/%s", appProperties.getResultsQueryPath(), HEARING_ID);
        log.info("Stubbing results-query-api url:{}", url);
        stubFor(WireMock.get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }
}