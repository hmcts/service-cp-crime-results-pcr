package uk.gov.hmcts.cp.servicebus.services;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;

import java.net.URI;

@Component
@AllArgsConstructor
public class ServiceBusClientFactory {

    private final ServiceBusProperties properties;

    public ServiceBusSenderClient senderClient() {
        return clientBuilder().sender().queueName(ServiceBusProperties.QUEUE_NAME).buildClient();
    }

    public ServiceBusClientBuilder.ServiceBusProcessorClientBuilder processorClientBuilder() {
        return clientBuilder().processor()
                .queueName(ServiceBusProperties.QUEUE_NAME)
                .disableAutoComplete();
    }

    private ServiceBusClientBuilder clientBuilder() {
        return properties.isEmulator()
                ? new ServiceBusClientBuilder().connectionString(properties.getConnectionString())
                : new ServiceBusClientBuilder()
                        .fullyQualifiedNamespace(URI.create(properties.getConnectionString()).getHost())
                        .credential(new DefaultAzureCredentialBuilder().build());
    }
}
