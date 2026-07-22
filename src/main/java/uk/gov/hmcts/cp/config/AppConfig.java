package uk.gov.hmcts.cp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.services.ClockService;

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
}
