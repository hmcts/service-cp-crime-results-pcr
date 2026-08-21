package uk.gov.hmcts.cp.servicebus.config;

import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@Slf4j
@Configuration
public class ServiceBusAdminConfiguration {

    private static final int EMULATOR_ADMIN_PORT = 5300;

    @Bean
    public ServiceBusAdministrationClient administrationClient(final ServiceBusProperties properties) {
        log.info("ServiceBusAdminConfiguration building administrationClient isEmulator:{}", properties.isEmulator());
        return properties.isEmulator() ? emulatorClient(properties) : azureClient(properties);
    }

    private ServiceBusAdministrationClient azureClient(final ServiceBusProperties properties) {
        return new ServiceBusAdministrationClientBuilder()
                .endpoint(properties.getConnectionString())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    private ServiceBusAdministrationClient emulatorClient(final ServiceBusProperties properties) {
        return new ServiceBusAdministrationClientBuilder()
                .connectionString(properties.getAdminConnectionString())
                .httpClient(emulatorHttpClient())
                .addPolicy(forceHttpPolicy())
                .buildClient();
    }

    private HttpClient emulatorHttpClient() {
        return new NettyAsyncHttpClientBuilder().port(EMULATOR_ADMIN_PORT).build();
    }

    private HttpPipelinePolicy forceHttpPolicy() {
        return (context, next) -> {
            try {
                final URL current = context.getHttpRequest().getUrl();
                final URL httpUrl = URI.create("http://" + current.getHost() + ":" + EMULATOR_ADMIN_PORT + current.getFile()).toURL();
                context.getHttpRequest().setUrl(httpUrl);
            } catch (MalformedURLException e) {
                return Mono.error(e);
            }
            return next.process();
        };
    }
}
