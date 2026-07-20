package uk.gov.hmcts.cp.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResultsQueryClient {

    private static final String ACCEPT_HEARING_DETAILS_INTERNAL =
            "application/vnd.results.hearing-details-internal+json";

    private final AppPropertiesBackend appProperties;
    private final RestClient restClient;

    public HearingDetailsResponse getHearingDetails(final UUID hearingId) {
        final String url = buildUrl(hearingId);
        log.info("Getting hearing details from {}", Encode.forJava(url));
        return restClient.get()
                .uri(url)
                .header("Accept", ACCEPT_HEARING_DETAILS_INTERNAL)
                .header("CJSCPPUID", appProperties.getResultsQueryCjscppuid())
                .retrieve()
                .body(HearingDetailsResponse.class);
    }

    private String buildUrl(final UUID hearingId) {
        return String.format("%s%s/%s", appProperties.getResultsQueryUrl(), appProperties.getResultsQueryPath(), hearingId);
    }
}