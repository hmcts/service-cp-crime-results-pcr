package uk.gov.hmcts.cp.smoketest;

import com.intuit.karate.http.HttpLogModifier;

import java.util.Set;

// Masks vault-sourced IDs/secrets (Entra client id/secret, APIM subscription key, CJSCPPUID, bearer tokens) out of Karate's logs.
public class SmokeTestLogModifier implements HttpLogModifier {

    private static final Set<String> MASKED_HEADERS =
            Set.of("authorization", "ocp-apim-subscription-key", "cjscppuid");
    private static final String MASK = "***";

    @Override
    public boolean enableForUri(final String uri) {
        return true;
    }

    @Override
    public String uri(final String uri) {
        return uri;
    }

    @Override
    public String header(final String name, final String value) {
        return MASKED_HEADERS.contains(name.toLowerCase()) ? MASK : value;
    }

    @Override
    public String request(final String uri, final String body) {
        if (body == null) {
            return null;
        }
        return body.replaceAll("client_secret=[^&]*", "client_secret=" + MASK)
                .replaceAll("client_id=[^&]*", "client_id=" + MASK);
    }

    @Override
    public String response(final String uri, final String body) {
        if (body == null) {
            return null;
        }
        return body.replaceAll("\"access_token\"\\s*:\\s*\"[^\"]*\"", "\"access_token\":\"" + MASK + "\"");
    }
}
