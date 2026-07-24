package uk.gov.hmcts.cp.clients;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.net.HttpURLConnection.HTTP_OK;

class ResultsClientTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String RESULTS_QUERY_PATH = "/results-query-api/query/api/rest/results/hearingDetails/internal";

    private WireMockServer wireMockServer;
    private ResultsClient resultsClient;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);

        final AppPropertiesBackend appProperties = new AppPropertiesBackend(
                "http://localhost:8081", RESULTS_QUERY_PATH, "00000000-0000-0000-0000-000000000000");
        resultsClient = new ResultsClient(appProperties, RestClient.create());
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void getHearingDetails_should_callCorrectUrlAndAcceptHeader() {
        final String url = String.format("%s/%s", RESULTS_QUERY_PATH, HEARING_ID);
        stubFor(WireMock.get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(readResourceContents("pcr/hearing-details-no-cases.json"))));

        resultsClient.getHearingDetails(HEARING_ID);

        verify(getRequestedFor(urlEqualTo(url))
                .withHeader("Accept", WireMock.equalTo("application/vnd.results.hearing-details-internal+json"))
                .withHeader("CJSCPPUID", WireMock.equalTo("00000000-0000-0000-0000-000000000000")));
    }

    @SneakyThrows
    private String readResourceContents(final String resourceName) {
        final URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}