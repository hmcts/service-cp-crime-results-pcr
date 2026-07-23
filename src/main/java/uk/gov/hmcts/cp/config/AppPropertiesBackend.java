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

    public AppPropertiesBackend(
            @Value("${results-query-client.url}") final String resultsQueryUrl,
            @Value("${results-query-client.path:/results-query-api/query/api/rest/results/hearingDetails/internal}") final String resultsQueryPath,
            @Value("${results-query-client.cjscppuid}") final String resultsQueryCjscppuid) {
        this.resultsQueryUrl = resultsQueryUrl;
        this.resultsQueryPath = resultsQueryPath;
        this.resultsQueryCjscppuid = resultsQueryCjscppuid;
    }
}
