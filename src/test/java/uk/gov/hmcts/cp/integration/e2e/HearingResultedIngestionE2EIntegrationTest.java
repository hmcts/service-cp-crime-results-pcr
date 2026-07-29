package uk.gov.hmcts.cp.integration.e2e;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import uk.gov.hmcts.cp.servicebus.services.HearingResultedProcessorService;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Real end-to-end proof: a real Event Grid envelope pointing at a real captured hearing payload
// (two-def-one-application — one physical defendant appearing both as a prosecutionCase
// defendant and as a court application's subject, sharing one masterDefendantId, per this
// repo's CLAUDE.md architecture rule) is seeded into a real Redis, run through the real
// HearingResultedProcessorService -> ResultsIngestionService -> CPVocabularyService ->
// CPResultsPcrOrchestrator -> repository stack against a real Postgres, with only the
// now-subscriptions HTTP call stubbed (WireMock) — proving the whole pipeline persists exactly
// what the real payload describes, not a hand-built unit-test fixture.
@ExtendWith(MockitoExtension.class)
class HearingResultedIngestionE2EIntegrationTest extends IngestionE2ETestBase {

    private static final UUID HEARING_ID = UUID.fromString("5f75863f-270a-482a-9e67-eaccb2fd3130");
    private static final String HEARING_DAY = "2026-07-23";
    private static final String CASE_URN = "TEST1234567";
    private static final UUID DEFENDANT_ID = UUID.fromString("ea6b2d84-e99a-47ff-b031-a036e093f627");
    private static final String FIXTURE_PATH = "pcr-two-def-one-application/two-def-one-application.json";
    private static final String CACHE_KEY = "INT_" + HEARING_ID + "_" + HEARING_DAY + "_result_";

    @Autowired
    private HearingResultedProcessorService processorService;
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

    @Mock
    private ServiceBusReceivedMessageContext context;
    @Mock
    private ServiceBusReceivedMessage message;

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
        stubNowSubscriptionsUnconditionalMatch();
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
    void onMessage_should_persistCaseHearingVersionAndCourtApplication_whenRealHearingPayloadInRedis() {
        redisTemplate.opsForValue().set(CACHE_KEY, readResourceContents(FIXTURE_PATH));
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(eventGridEnvelope()));

        processorService.onMessage(context);

        verify(context).complete();
        verify(context, never()).deadLetter();

        final CPCaseHearingEntity caseHearing = caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)
                .orElseThrow(() -> new AssertionError("Expected a CPCaseHearingEntity for " + CASE_URN + "/" + HEARING_ID));
        assertThat(caseHearing.getCourtHouseCode()).isEqualTo("B52CM00");
        assertThat(caseHearing.getCourtHouseName()).isEqualTo("Bristol Magistrates' Court");
        assertThat(caseHearing.getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));

        final CPVersionEntity version = versionRepository.findAll().stream()
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

        final List<CPCourtApplicationEntity> applications = courtApplicationRepository.findAll().stream()
                .filter(a -> version.getCpVersionPk().equals(a.getVersionPk()))
                .toList();
        assertThat(applications).hasSize(1);
        assertThat(applications.get(0).getSourceApplicationId())
                .isEqualTo(UUID.fromString("08efcf9b-c3d6-439e-bdbc-509ee4126921"));
        assertThat(applications.get(0).getReference()).isEqualTo(CASE_URN);
        assertThat(applications.get(0).getType()).isEqualTo("Application within criminal proceedings");

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
        final CPJudicialResultEntity imprisonment = judicialResults.get(0);
        assertThat(imprisonment.getResultCode()).isEqualTo("1002");
        assertThat(imprisonment.getConvicted()).isTrue();
        assertThat(imprisonment.getFinancial()).isFalse();
        assertThat(imprisonment.getImprisonmentPeriod()).isEqualTo("6 Months");
        assertThat(imprisonment.getTotalCustodialPeriod()).isEqualTo("5 Months");

        final List<CPJudicialResultPromptEntity> prompts = judicialResultPromptRepository.findAll().stream()
                .filter(p -> imprisonment.getId().equals(p.getJudicialResultId()))
                .toList();
        assertThat(prompts).extracting(CPJudicialResultPromptEntity::getPromptReference)
                .contains("imprisonmentPeriod", "totalCustodialPeriod", "prisonOrganisationName");
    }

    private void stubNowSubscriptionsUnconditionalMatch() {
        WireMock.stubFor(get(urlPathEqualTo("/referencedata-query-api/query/api/rest/referencedata/now-subscriptions"))
                .willReturn(aResponse()
                        .withStatus(HTTP_OK)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "nowSubscriptions": [
                                    { "isPrisonCourtRegisterSubscription": true, "applySubscriptionRules": false }
                                  ]
                                }
                                """)));
    }

    private String eventGridEnvelope() {
        return readResourceContents("servicebus/hearing-resulted-event-grid-envelope.json")
                .formatted(HEARING_ID, HEARING_ID, HEARING_DAY);
    }

    @SneakyThrows
    private String readResourceContents(final String resourceName) {
        final URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}
