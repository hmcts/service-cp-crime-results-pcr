package uk.gov.hmcts.cp.clients.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscriptionsResponse;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataClient {

    private static final String ACCEPT_NOW_SUBSCRIPTIONS =
            "application/vnd.referencedata.query.get-now-subscriptions+json";
    public static final String REFERENCE_DATA_PATH =
            "/referencedata-query-api/query/api/rest/referencedata/now-subscriptions";

    private final AppPropertiesBackend appProperties;
    private final RestClient restClient;

    public List<CPNowSubscription> getPrisonCourtRegisterSubscriptions(final LocalDate activeAt) {
        final String url = buildUrl(activeAt);
        log.info("Getting now-subscriptions from {}", Encode.forJava(url));
        final CPNowSubscriptionsResponse response = restClient.get()
                .uri(url)
                .header("Accept", ACCEPT_NOW_SUBSCRIPTIONS)
                .header("CJSCPPUID", appProperties.getReferenceDataCjscppuid())
                .retrieve()
                .body(CPNowSubscriptionsResponse.class);
        return response == null || response.getNowSubscriptions() == null
                ? List.of()
                : response.getNowSubscriptions();
    }

    private String buildUrl(final LocalDate activeAt) {
        return UriComponentsBuilder
                .fromUriString(appProperties.getReferenceDataUrl() + REFERENCE_DATA_PATH)
                .queryParam("on", activeAt)
                .toUriString();
    }
}