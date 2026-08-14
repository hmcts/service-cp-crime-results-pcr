package uk.gov.hmcts.cp.integration.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.repositories.CPCaseHearingRepository;
import uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultRepository;
import uk.gov.hmcts.cp.repositories.CPOffenceRepository;
import uk.gov.hmcts.cp.repositories.CPVersionRepository;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HearingResultedIngestionE2EIntegrationTest extends IngestionE2ETestBase {

    private static final UUID HEARING_ID = UUID.fromString("5f75863f-270a-482a-9e67-eaccb2fd3130");
    private static final String HEARING_DAY = "2026-07-23";
    private static final String CASE_URN = "TEST1234567";
    private static final UUID DEFENDANT_ID = UUID.fromString("ea6b2d84-e99a-47ff-b031-a036e093f627");
    private static final String FIXTURE_PATH = "pcr-two-def-one-application/two-def-one-application.json";
    private static final String NOW_SUBSCRIPTIONS_FIXTURE_PATH = "referencedata/now-subscriptions-prison-court-register-fixture.json";
    private static final String WEBHOOK_EVENT_FIXTURE_PATH = "webhook/hearing-resulted-webhook-event.json";
    private static final String CACHE_KEY = "INT_" + HEARING_ID + "_" + HEARING_DAY + "_result_";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CPCaseHearingRepository caseHearingRepository;
    @Autowired
    private CPVersionRepository versionRepository;
    @Autowired
    private CPCourtApplicationRepository courtApplicationRepository;
    @Autowired
    private CPOffenceRepository offenceRepository;
    @Autowired
    private CPJudicialResultRepository judicialResultRepository;
    @Autowired
    private CPJudicialResultPromptRepository judicialResultPromptRepository;

    private WireMockServer wireMockServer;
    private CPCaseHearingEntity caseHearing;
    private CPVersionEntity version;
    private CPJudicialResultEntity imprisonment;

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
        redisTemplate.delete(CACHE_KEY);
    }

    @Transactional
    @Test
    void twoDefendantOneApplicationHearing_should_persistAndExposeViaGetPcr_whenPrisonCourtRegisterSubscriptionMatches() throws Exception {
        given_a_matching_prison_court_register_subscription();
        given_the_real_hearing_payload_is_seeded_in_redis();

        when_the_hearing_resulted_webhook_is_received();

        then_the_case_hearing_is_persisted();
        then_the_version_is_persisted_with_defendant_pii_and_custody();
        then_the_court_application_is_persisted();
        then_the_offence_and_judicial_result_are_persisted();
        then_the_judicial_result_prompts_are_persisted();
        then_the_get_pcr_query_returns_the_persisted_result();
    }

    private void given_a_matching_prison_court_register_subscription() {
        WireMock.stubFor(get(urlPathEqualTo("/referencedata-query-api/query/api/rest/referencedata/now-subscriptions"))
                .willReturn(aResponse()
                        .withStatus(HTTP_OK)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readResourceContents(NOW_SUBSCRIPTIONS_FIXTURE_PATH))));
    }

    private void given_the_real_hearing_payload_is_seeded_in_redis() {
        redisTemplate.opsForValue().set(CACHE_KEY, readResourceContents(FIXTURE_PATH));
    }

    private void when_the_hearing_resulted_webhook_is_received() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/hearing-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hearingResultedWebhookEvent()))
                .andExpect(status().isOk());
    }

    private void then_the_case_hearing_is_persisted() {
        caseHearing = caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)
                .orElseThrow(() -> new AssertionError("Expected a CPCaseHearingEntity for " + CASE_URN + "/" + HEARING_ID));
        assertThat(caseHearing.getCourtHouseCode()).isEqualTo("B52CM00");
        assertThat(caseHearing.getCourtHouseName()).isEqualTo("Bristol Magistrates' Court");
        assertThat(caseHearing.getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
    }

    private void then_the_version_is_persisted_with_defendant_pii_and_custody() {
        version = versionRepository.findAll().stream()
                .filter(v -> DEFENDANT_ID.equals(v.getDefendantId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a CPVersionEntity for defendantId " + DEFENDANT_ID));
        assertThat(version.getMasterDefendantId()).isEqualTo(DEFENDANT_ID);
        assertThat(version.getCaseHearingId()).isEqualTo(caseHearing.getId());
        assertThat(version.getCustodyLocation()).isEqualTo("HMP/YOI Eastwood Park");
        assertThat(version.getCustodyType()).isEqualTo("Prison");
        assertThat(version.getFirstName()).isEqualTo("Sophie");
        assertThat(version.getLastName()).isEqualTo("Reichel");
        assertThat(version.getTitle()).isEqualTo("Mr");
        assertThat(version.getDateOfBirth()).isEqualTo(LocalDate.of(2006, 7, 23));
        assertThat(version.getAddressLine1()).isEqualTo("30 Church Street");
        assertThat(version.getPostCode()).isEqualTo("NW1 5BR");
    }

    private void then_the_court_application_is_persisted() {
        final List<CPCourtApplicationEntity> applications = courtApplicationRepository.findAll().stream()
                .filter(a -> version.getCpVersionPk().equals(a.getVersionPk()))
                .toList();
        assertThat(applications).hasSize(1);
        assertThat(applications.get(0).getSourceApplicationId())
                .isEqualTo(UUID.fromString("08efcf9b-c3d6-439e-bdbc-509ee4126921"));
        assertThat(applications.get(0).getReference()).isEqualTo(CASE_URN);
        assertThat(applications.get(0).getType()).isEqualTo("Application within criminal proceedings");
    }

    private void then_the_offence_and_judicial_result_are_persisted() {
        final List<CPOffenceEntity> offences = offenceRepository.findAll().stream()
                .filter(o -> version.getCpVersionPk().equals(o.getVersionPk()))
                .toList();
        assertThat(offences).hasSize(1);
        assertThat(offences.get(0).getCode()).isEqualTo("TH68013A");
        assertThat(offences.get(0).getSourceOffenceId())
                .isEqualTo(UUID.fromString("aac5259f-93ac-4abc-85df-6610b8d52cb7"));

        final List<CPJudicialResultEntity> judicialResults = judicialResultRepository.findAll().stream()
                .filter(r -> offences.get(0).getId().equals(r.getOffenceId()))
                .toList();
        assertThat(judicialResults).hasSize(1);
        imprisonment = judicialResults.get(0);
        assertThat(imprisonment.getResultCode()).isEqualTo("1002");
        assertThat(imprisonment.getConvicted()).isTrue();
        assertThat(imprisonment.getFinancial()).isFalse();
        assertThat(imprisonment.getImprisonmentPeriod()).isEqualTo("6 Months");
        assertThat(imprisonment.getTotalCustodialPeriod()).isEqualTo("5 Months");
    }

    private void then_the_judicial_result_prompts_are_persisted() {
        final List<CPJudicialResultPromptEntity> prompts = judicialResultPromptRepository.findAll().stream()
                .filter(p -> imprisonment.getId().equals(p.getJudicialResultId()))
                .toList();
        assertThat(prompts).extracting(CPJudicialResultPromptEntity::getPromptReference)
                .contains("imprisonmentPeriod", "totalCustodialPeriod", "prisonOrganisationName");
    }

    private void then_the_get_pcr_query_returns_the_persisted_result() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(
                        "/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        CASE_URN, HEARING_ID, DEFENDANT_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prosecutionCase.caseURN").value(CASE_URN))
                .andExpect(jsonPath("$[0].defendant.masterDefendantId").value(DEFENDANT_ID.toString()))
                .andExpect(jsonPath("$[0].defendant.firstName").value("Sophie"))
                .andExpect(jsonPath("$[0].defendant.lastName").value("Reichel"))
                .andExpect(jsonPath("$[0].defendant.title").value("Mr"))
                .andExpect(jsonPath("$[0].defendant.dateOfBirth").value("2006-07-23"))
                .andExpect(jsonPath("$[0].defendant.address.address1").value("30 Church Street"))
                .andExpect(jsonPath("$[0].defendant.address.postCode").value("NW1 5BR"))
                .andExpect(jsonPath("$[0].custodyLocation.name").value("HMP/YOI Eastwood Park"))
                .andExpect(jsonPath("$[0].custodyLocation.custodyType").value("Prison"))
                .andExpect(jsonPath("$[0].hearing.courtDetails.court.courtHouseCode").value("B52CM00"))
                .andExpect(jsonPath("$[0].hearing.courtDetails.court.courtHouseName").value("Bristol Magistrates' Court"))
                .andExpect(jsonPath("$[0].hearing.hearingDate").value("2026-07-23"))
                .andExpect(jsonPath("$[0].courtApplications[0].reference").value(CASE_URN))
                .andExpect(jsonPath("$[0].courtApplications[0].type").value("Application within criminal proceedings"))
                .andExpect(jsonPath("$[0].offences[0].code").value("TH68013A"))
                .andExpect(jsonPath("$[0].offences[0].results[0].resultTexts[*].label")
                        .value(hasItems("Imprisonment Period", "Total custodial period", "Prison organisation name")));
    }

    private String hearingResultedWebhookEvent() {
        return readResourceContents(WEBHOOK_EVENT_FIXTURE_PATH)
                .formatted(HEARING_ID, HEARING_ID, HEARING_DAY);
    }

    @SneakyThrows
    private String readResourceContents(final String resourceName) {
        final URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}