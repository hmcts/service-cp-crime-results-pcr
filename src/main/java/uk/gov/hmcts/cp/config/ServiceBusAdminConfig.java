package uk.gov.hmcts.cp.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ServiceBusAdminConfig {

    private final ServiceBusProperties properties;

    @Bean
    public ServiceBusAdministrationClient serviceBusAdministrationClient() {
        final ServiceBusAdministrationClientBuilder builder;
        if (properties.isEmulator()) {
            builder = new ServiceBusAdministrationClientBuilder().connectionString(properties.getAdminConnection());
        } else {
            builder = new ServiceBusAdministrationClientBuilder()
                    .endpoint(properties.getConnection())
                    .credential(new DefaultAzureCredentialBuilder().build());
        }
        return builder.buildClient();
    }
}