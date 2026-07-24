package uk.gov.hmcts.cp.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.NowSubscription;
import uk.gov.hmcts.cp.domain.NowSubscriptionsResponse;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataClient {

    private static final String ACCEPT_NOW_SUBSCRIPTIONS =
            "application/vnd.referencedata.query.get-now-subscriptions+json";

    private final AppPropertiesBackend appProperties;
    private final RestClient restClient;

    public List<NowSubscription> getPrisonCourtRegisterSubscriptions(final LocalDate on) {
        final String url = buildUrl(on);
        log.info("Getting now-subscriptions from {}", Encode.forJava(url));
        final NowSubscriptionsResponse response = restClient.get()
                .uri(url)
                .header("Accept", ACCEPT_NOW_SUBSCRIPTIONS)
                .header("CJSCPPUID", appProperties.getReferenceDataCjscppuid())
                .retrieve()
                .body(NowSubscriptionsResponse.class);
        return response == null || response.getNowSubscriptions() == null
                ? List.of()
                : response.getNowSubscriptions();
    }

    private String buildUrl(final LocalDate on) {
        return UriComponentsBuilder
                .fromUriString(appProperties.getReferenceDataUrl() + appProperties.getReferenceDataPath())
                .queryParam("on", on)
                .toUriString();
    }
}