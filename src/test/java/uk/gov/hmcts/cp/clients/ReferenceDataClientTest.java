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
import uk.gov.hmcts.cp.domain.NowSubscription;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;

class ReferenceDataClientTest {

    private static final String REFERENCE_DATA_PATH =
            "/referencedata-query-api/query/api/rest/referencedata/now-subscriptions";
    private static final LocalDate ON_DATE = LocalDate.of(2026, 7, 23);

    private WireMockServer wireMockServer;
    private ReferenceDataClient referenceDataClient;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);

        final AppPropertiesBackend appProperties = new AppPropertiesBackend(
                "http://localhost:8081", "/results-query-api/query/api/rest/results/hearingDetails/internal",
                "00000000-0000-0000-0000-000000000000",
                "http://localhost:8081", REFERENCE_DATA_PATH, "00000000-0000-0000-0000-000000000000");
        referenceDataClient = new ReferenceDataClient(appProperties, RestClient.create());
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void getPrisonCourtRegisterSubscriptions_should_callCorrectUrlAndAcceptHeader() {
        stubFor(readResourceContents("referencedata/now-subscriptions-one-pcr.json"));

        referenceDataClient.getPrisonCourtRegisterSubscriptions(ON_DATE);

        verify(getRequestedFor(urlPathEqualTo(REFERENCE_DATA_PATH))
                .withQueryParam("on", WireMock.equalTo("2026-07-23"))
                .withHeader("Accept",
                        WireMock.equalTo("application/vnd.referencedata.query.get-now-subscriptions+json"))
                .withHeader("CJSCPPUID", WireMock.equalTo("00000000-0000-0000-0000-000000000000")));
    }

    @Test
    void getPrisonCourtRegisterSubscriptions_should_returnParsedSubscriptions() {
        stubFor(readResourceContents("referencedata/now-subscriptions-one-pcr.json"));

        final List<NowSubscription> subscriptions = referenceDataClient.getPrisonCourtRegisterSubscriptions(ON_DATE);

        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).isPrisonCourtRegisterSubscription()).isTrue();
    }

    private void stubFor(final String body) {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(REFERENCE_DATA_PATH)).willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    @SneakyThrows
    private String readResourceContents(final String resourceName) {
        final URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}