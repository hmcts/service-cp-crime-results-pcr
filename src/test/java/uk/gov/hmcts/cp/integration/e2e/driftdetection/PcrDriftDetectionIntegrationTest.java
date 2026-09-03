package uk.gov.hmcts.cp.integration.e2e.driftdetection;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.integration.e2e.IngestionE2ETestBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PcrDriftDetectionIntegrationTest extends IngestionE2ETestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private StringRedisTemplate redisTemplate;

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

    @Transactional
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    void replayedHearing_should_matchExpectedPcrOutput_forEveryDefendant(final DriftFixture fixture) throws Exception {
        final HearingIdentity identity = parseIdentity(fixture.root());

        stubNowSubscriptions(fixture.root());
        seedRedis(fixture.root(), identity);
        postHearingResultedEvent(identity);

        try (Stream<Path> expectedFiles = Files.list(fixture.root().resolve("expected"))) {
            for (final Path expectedFile : expectedFiles.toList()) {
                final String defendantId = expectedFile.getFileName().toString().replace(".json", "");
                final String caseUrn = identity.caseUrnByDefendantId().get(defendantId);
                assertMatchesExpected(identity.hearingId(), caseUrn, defendantId, expectedFile);
            }
        }
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

    private void postHearingResultedEvent(final HearingIdentity identity) throws Exception {
        final String body = """
                [{
                  "id": "evt-1",
                  "eventType": "Hearing_Resulted",
                  "subject": "hearing/%s",
                  "eventTime": "2026-07-31T09:00:00.000Z",
                  "data": { "hearingId": "%s", "hearingDay": "%s", "userId": "00000000-0000-0000-0000-000000000099" }
                }]
                """.formatted(identity.hearingId(), identity.hearingId(), identity.hearingDay());

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/hearing-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void assertMatchesExpected(final String hearingId, final String caseUrn, final String defendantId,
                                        final Path expectedFile) throws Exception {
        final String expectedJson = Files.readString(expectedFile);
        final String actualJson = mockMvc.perform(MockMvcRequestBuilders.get(
                        "/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        caseUrn, hearingId, defendantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.NON_EXTENSIBLE);
    }

    private HearingIdentity parseIdentity(final Path fixtureRoot) throws Exception {
        final JsonNode hearing = OBJECT_MAPPER.readTree(Files.readString(fixtureRoot.resolve("event.json"))).get("hearing");
        final String hearingId = hearing.get("id").asString();
        final String hearingDay = hearing.get("hearingDays").get(0).get("sittingDay").asString().substring(0, 10);
        final Map<String, String> caseUrnByDefendantId = new HashMap<>();
        addProsecutionCaseDefendants(hearing, caseUrnByDefendantId);
        addApplicationOnlyDefendants(hearing, caseUrnByDefendantId);
        return new HearingIdentity(hearingId, hearingDay, caseUrnByDefendantId);
    }

    private void addProsecutionCaseDefendants(final JsonNode hearing, final Map<String, String> caseUrnByDefendantId) {
        final JsonNode prosecutionCases = hearing.get("prosecutionCases");
        if (prosecutionCases == null) {
            return;
        }
        for (final JsonNode prosecutionCase : prosecutionCases) {
            final String caseUrn = prosecutionCase.get("prosecutionCaseIdentifier").get("caseURN").asString();
            for (final JsonNode defendant : prosecutionCase.get("defendants")) {
                caseUrnByDefendantId.put(defendant.get("id").asString(), caseUrn);
            }
        }
    }

    // Mirrors CPHearingResultEntityMapper.applicationOnlyDefendant/resolveDefendantId — case URN
    // is applicationReference, matching production (design doc 2026-09-02).
    private void addApplicationOnlyDefendants(final JsonNode hearing, final Map<String, String> caseUrnByDefendantId) {
        final JsonNode courtApplications = hearing.get("courtApplications");
        if (courtApplications == null) {
            return;
        }
        for (final JsonNode application : courtApplications) {
            final JsonNode subject = application.get("subject");
            final JsonNode masterDefendant = subject == null ? null : subject.get("masterDefendant");
            if (masterDefendant == null) {
                continue;
            }
            final String defendantId = resolveDefendantId(masterDefendant, application);
            if (defendantId != null) {
                caseUrnByDefendantId.putIfAbsent(defendantId, application.get("applicationReference").asString());
            }
        }
    }

    private String resolveDefendantId(final JsonNode masterDefendant, final JsonNode application) {
        final JsonNode defendantCase = masterDefendant.get("defendantCase");
        if (defendantCase == null || defendantCase.size() == 0) {
            return null;
        }
        if (defendantCase.size() == 1) {
            return defendantCase.get(0).get("defendantId").asString();
        }
        final Set<String> applicationCaseIds = new HashSet<>();
        final JsonNode courtApplicationCases = application.get("courtApplicationCases");
        if (courtApplicationCases != null) {
            for (final JsonNode cac : courtApplicationCases) {
                final JsonNode prosecutionCaseId = cac.get("prosecutionCaseId");
                if (prosecutionCaseId != null) {
                    applicationCaseIds.add(prosecutionCaseId.asString());
                }
            }
        }
        for (final JsonNode dc : defendantCase) {
            if (applicationCaseIds.contains(dc.get("caseId").asString())) {
                return dc.get("defendantId").asString();
            }
        }
        return null;
    }

    private record HearingIdentity(String hearingId, String hearingDay, Map<String, String> caseUrnByDefendantId) {
    }
}
