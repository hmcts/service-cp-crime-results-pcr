package uk.gov.hmcts.cp.auth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@Getter
public class EntraAuthProperties {

    private static final Set<String> DEPLOYED_ENVIRONMENTS = Set.of("DEV", "STE", "SIT", "PRP", "PRD");
    private static final int MAX_CLOCK_SKEW_SECONDS = 300;

    private final AuthMode mode;
    private final String tenantId;
    private final String audience;
    private final String issuer;
    private final String jwksUri;
    private final int clockSkewSeconds;
    private final int jwksCacheTtlSeconds;

    public EntraAuthProperties(
            @Value("${auth.mode}") final AuthMode mode,
            @Value("${auth.tenant-id}") final String tenantId,
            @Value("${auth.audience}") final String audience,
            @Value("${auth.issuer}") final String issuer,
            @Value("${auth.jwks-uri}") final String jwksUri,
            @Value("${auth.clock-skew-seconds}") final int clockSkewSeconds,
            @Value("${auth.jwks-cache-ttl-seconds}") final int jwksCacheTtlSeconds,
            @Value("${environment.name}") final String environmentName) {
        Assert.state(mode == AuthMode.ENFORCE || !DEPLOYED_ENVIRONMENTS.contains(environmentName),
                () -> "auth.mode must be ENFORCE in a deployed environment (" + environmentName
                        + "); OFF/OBSERVE provide no protection and are not permitted there");
        Assert.state(mode == AuthMode.OFF || (StringUtils.hasText(tenantId) && StringUtils.hasText(audience)),
                "auth.tenant-id and auth.audience must be set once auth.mode is not OFF");
        this.mode = mode;
        this.tenantId = tenantId;
        this.audience = audience;
        this.issuer = StringUtils.hasText(issuer) ? issuer : deriveIssuer(tenantId);
        this.jwksUri = StringUtils.hasText(jwksUri) ? jwksUri : deriveJwksUri(this.issuer);
        this.clockSkewSeconds = Math.min(clockSkewSeconds, MAX_CLOCK_SKEW_SECONDS);
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
    }

    private static String deriveIssuer(final String tenantId) {
        return "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    }

    private static String deriveJwksUri(final String issuer) {
        return issuer + "/discovery/v2.0/keys";
    }
}
