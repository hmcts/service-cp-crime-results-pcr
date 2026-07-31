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
        postWebhook(identity);

        try (Stream<Path> expectedFiles = Files.list(fixture.root().resolve("expected"))) {
            for (final Path expectedFile : expectedFiles.toList()) {
                final String defendantId = expectedFile.getFileName().toString().replace(".json", "");
                assertMatchesExpected(identity, defendantId, expectedFile);
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

    private void postWebhook(final HearingIdentity identity) throws Exception {
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

    private void assertMatchesExpected(final HearingIdentity identity, final String defendantId,
                                        final Path expectedFile) throws Exception {
        final String expectedJson = Files.readString(expectedFile);
        final String actualJson = mockMvc.perform(MockMvcRequestBuilders.get(
                        "/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        identity.caseUrn(), identity.hearingId(), defendantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
    }

    private HearingIdentity parseIdentity(final Path fixtureRoot) throws Exception {
        final JsonNode hearing = OBJECT_MAPPER.readTree(Files.readString(fixtureRoot.resolve("event.json"))).get("hearing");
        final String hearingId = hearing.get("id").asString();
        final String hearingDay = hearing.get("hearingDays").get(0).get("sittingDay").asString().substring(0, 10);
        final String caseUrn = hearing.get("prosecutionCases").get(0).get("prosecutionCaseIdentifier").get("caseURN").asString();
        return new HearingIdentity(hearingId, hearingDay, caseUrn);
    }

    private record HearingIdentity(String hearingId, String hearingDay, String caseUrn) {
    }
}
