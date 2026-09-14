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

@Configuration
public class AppConfig {

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
        final long timeToLiveMillis = properties.getJwksCacheTtlSeconds() * 1000L;
        // refresh-time must leave room for the builder's own refresh-ahead buffer, so it can't
        // equal time-to-live — refreshing at the halfway point is a sane, simple choice.
        final long refreshTimeMillis = timeToLiveMillis / 2;
        return JWKSourceBuilder.<SecurityContext>create(URI.create(properties.getJwksUri()).toURL())
                .cache(timeToLiveMillis, refreshTimeMillis)
                .build();
    }
}
