package uk.gov.hmcts.cp.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.auth.EntraAuthProperties;
import uk.gov.hmcts.cp.services.ClockService;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfig {

    // Max time one JWKS refresh attempt may take — not a refresh interval. A small fixed
    // value, not derived from the cache TTL.
    private static final long JWKS_REFRESH_TIMEOUT_MILLIS = Duration.ofSeconds(15).toMillis();
    private static final long JWKS_MIN_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    @Bean
    public RestClient restClient(final OutboundTracingInterceptor outboundTracingInterceptor) {
        return RestClient.builder()
                .requestInterceptor(outboundTracingInterceptor)
                .build();
    }

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }

    // Nimbus's own JWKS fetch/cache, not RestClient — JWKSourceBuilder is the library's own
    // proven resource retrieval, no behavioural gain from wrapping it. Never fetched when
    // auth.mode is OFF, so a blank/local jwks-uri is harmless until validation actually runs.
    @Bean
    public JWKSource<SecurityContext> jwkSource(final EntraAuthProperties properties) throws MalformedURLException {
        final long timeToLiveMillis = Duration.ofSeconds(properties.getJwksCacheTtlSeconds()).toMillis();
        return JWKSourceBuilder.<SecurityContext>create(URI.create(properties.getJwksUri()).toURL())
                .cache(timeToLiveMillis, JWKS_REFRESH_TIMEOUT_MILLIS)
                .rateLimited(JWKS_MIN_REFRESH_INTERVAL_MILLIS)
                .retrying(true)
                .build();
    }
}
