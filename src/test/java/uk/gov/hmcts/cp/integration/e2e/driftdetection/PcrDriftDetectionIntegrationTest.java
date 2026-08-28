package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.integration.e2e.IngestionE2ETestBase;
import uk.gov.hmcts.cp.repositories.CPCaseHearingRepository;
import uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultRepository;
import uk.gov.hmcts.cp.repositories.CPOffenceRepository;
import uk.gov.hmcts.cp.repositories.CPVersionRepository;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "service-bus.auto-start-processors=true")
class PcrDriftDetectionIntegrationTest extends IngestionE2ETestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration AWAIT_PERSISTENCE = Duration.ofSeconds(15);
    private static final Duration AWAIT_POLL_INTERVAL = Duration.ofMillis(500);

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ServiceBusClientFactory clientFactory;
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
    private UUID persistedHearingId;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
    }

    // Persistence here happens on the Service Bus consumer thread, not this test's own thread -
    // @Transactional only rolls back the calling thread's transaction, so it can't undo rows
    // committed by that consumer. Cleaned up explicitly instead, same as
    // HearingResultedServiceBusE2EIntegrationTest.
    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        cleanUpPersistedData();
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    void replayedHearing_should_matchExpectedPcrOutput_forEveryDefendant(final DriftFixture fixture) throws Exception {
        final HearingIdentity identity = parseIdentity(fixture.root());
        persistedHearingId = UUID.fromString(identity.hearingId());

        stubNowSubscriptions(fixture.root());
        seedRedis(fixture.root(), identity);
        publishHearingResultedEventToQueue(identity);

        try (Stream<Path> expectedFiles = Files.list(fixture.root().resolve("expected"))) {
            for (final Path expectedFile : expectedFiles.toList()) {
                final String defendantId = expectedFile.getFileName().toString().replace(".json", "");
                final String caseUrn = identity.caseUrnByDefendantId().get(defendantId);
                assertMatchesExpected(identity.hearingId(), caseUrn, defendantId, expectedFile);
            }
        }
    }

    private void cleanUpPersistedData() {
        if (persistedHearingId == null) {
            return;
        }
        final List<CPCaseHearingEntity> caseHearings = caseHearingRepository.findAll().stream()
                .filter(c -> persistedHearingId.equals(c.getHearingId()))
                .toList();
        caseHearings.forEach(this::cleanUpCaseHearing);
    }

    private void cleanUpCaseHearing(final CPCaseHearingEntity caseHearing) {
        versionRepository.findAll().stream()
                .filter(v -> caseHearing.getId().equals(v.getCaseHearingId()))
                .forEach(this::cleanUpVersion);
        caseHearingRepository.delete(caseHearing);
    }

    private void cleanUpVersion(final CPVersionEntity version) {
        final List<CPOffenceEntity> offences = offenceRepository.findAll().stream()
                .filter(o -> version.getCpVersionPk().equals(o.getVersionPk()))
                .toList();
        offences.forEach(this::cleanUpOffence);
        courtApplicationRepository.findAll().stream()
                .filter(a -> version.getCpVersionPk().equals(a.getVersionPk()))
                .forEach(courtApplicationRepository::delete);
        versionRepository.delete(version);
    }

    private void cleanUpOffence(final CPOffenceEntity offence) {
        judicialResultRepository.findAll().stream()
                .filter(r -> offence.getId().equals(r.getOffenceId()))
                .forEach(this::cleanUpJudicialResult);
        offenceRepository.delete(offence);
    }

    private void cleanUpJudicialResult(final CPJudicialResultEntity judicialResult) {
        judicialResultPromptRepository.findAll().stream()
                .filter(p -> judicialResult.getId().equals(p.getJudicialResultId()))
                .forEach(judicialResultPromptRepository::delete);
        judicialResultRepository.delete(judicialResult);
    }

    private void stubNowSubscriptions(final Path fixtureRoot) throws Exception {
        WireMock.stubFor(get(urlPathEqualTo("/referencedata-query-api/query/api/rest/referencedata/now-subscriptions"))
                .willReturn(aResponse()
                        .withStatus(HTTP_OK)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Files.readString(fixtureRoot.resolve("now-subscriptions.json")))));
    }

    private void seedRedis(final Path fixtureRoot, final HearingIdentity identity) throws Exception {
        final String cacheKey = "INT_" + identity.hearingId() + "_" + identity.hearingDay() + "_result_";
        redisTemplate.opsForValue().set(cacheKey, Files.readString(fixtureRoot.resolve("event.json")));
    }

    private void publishHearingResultedEventToQueue(final HearingIdentity identity) {
        final String body = """
                {
                  "id": "evt-1",
                  "eventType": "Hearing_Resulted",
                  "subject": "hearing/%s",
                  "eventTime": "2026-07-31T09:00:00.000Z",
                  "data": { "hearingId": "%s", "hearingDay": "%s", "userId": "00000000-0000-0000-0000-000000000099" }
                }
                """.formatted(identity.hearingId(), identity.hearingId(), identity.hearingDay());

        try (ServiceBusSenderClient sender = clientFactory.senderClient()) {
            sender.sendMessage(new ServiceBusMessage(body));
        }
    }

    private void assertMatchesExpected(final String hearingId, final String caseUrn, final String defendantId,
                                        final Path expectedFile) throws Exception {
        final String expectedJson = Files.readString(expectedFile);

        await().atMost(AWAIT_PERSISTENCE)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .untilAsserted(() -> {
                    final String actualJson = mockMvc.perform(MockMvcRequestBuilders.get(
                                    "/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                                    caseUrn, hearingId, defendantId))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();

                    JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.NON_EXTENSIBLE);
                });
    }

    private HearingIdentity parseIdentity(final Path fixtureRoot) throws Exception {
        final JsonNode hearing = OBJECT_MAPPER.readTree(Files.readString(fixtureRoot.resolve("event.json"))).get("hearing");
        final String hearingId = hearing.get("id").asString();
        final String hearingDay = hearing.get("hearingDays").get(0).get("sittingDay").asString().substring(0, 10);
        final Map<String, String> caseUrnByDefendantId = new HashMap<>();
        for (final JsonNode prosecutionCase : hearing.get("prosecutionCases")) {
            final String caseUrn = prosecutionCase.get("prosecutionCaseIdentifier").get("caseURN").asString();
            for (final JsonNode defendant : prosecutionCase.get("defendants")) {
                caseUrnByDefendantId.put(defendant.get("id").asString(), caseUrn);
            }
        }
        return new HearingIdentity(hearingId, hearingDay, caseUrnByDefendantId);
    }

    private record HearingIdentity(String hearingId, String hearingDay, Map<String, String> caseUrnByDefendantId) {
    }
}
