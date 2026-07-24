package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class AppPropertiesBackend {

    private final String resultsQueryUrl;
    private final String resultsQueryPath;
    private final String resultsQueryCjscppuid;
    private final String referenceDataUrl;
    private final String referenceDataPath;
    private final String referenceDataCjscppuid;

    public AppPropertiesBackend(
            @Value("${results-query-client.url}") final String resultsQueryUrl,
            @Value("${results-query-client.path:/results-query-api/query/api/rest/results/hearingDetails/internal}") final String resultsQueryPath,
            @Value("${results-query-client.cjscppuid}") final String resultsQueryCjscppuid,
            @Value("${reference-data-client.url}") final String referenceDataUrl,
            @Value("${reference-data-client.path:/referencedata-query-api/query/api/rest/referencedata/now-subscriptions}") final String referenceDataPath,
            @Value("${reference-data-client.cjscppuid}") final String referenceDataCjscppuid) {
        this.resultsQueryUrl = resultsQueryUrl;
        this.resultsQueryPath = resultsQueryPath;
        this.resultsQueryCjscppuid = resultsQueryCjscppuid;
        this.referenceDataUrl = referenceDataUrl;
        this.referenceDataPath = referenceDataPath;
        this.referenceDataCjscppuid = referenceDataCjscppuid;
    }
}
