package uk.gov.hmcts.cp.clients;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.ServiceBusProperties;

import java.net.URI;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class HearingResultedServiceBusClientFactory {

    private final ServiceBusProperties properties;

    public ServiceBusProcessorClient processorClient(final Consumer<ServiceBusReceivedMessageContext> onMessage,
                                                       final Consumer<ServiceBusErrorContext> onError) {
        return clientBuilder().processor()
                .queueName(properties.getQueueName())
                .processMessage(onMessage::accept)
                .processError(onError::accept)
                .buildProcessorClient();
    }

    public ServiceBusSenderClient senderClient() {
        return clientBuilder().sender().queueName(properties.getQueueName()).buildClient();
    }

    private ServiceBusClientBuilder clientBuilder() {
        final ServiceBusClientBuilder builder;
        if (properties.isEmulator()) {
            builder = new ServiceBusClientBuilder().connectionString(properties.getConnection());
        } else {
            builder = new ServiceBusClientBuilder()
                    .fullyQualifiedNamespace(URI.create(properties.getConnection()).getHost())
                    .credential(new DefaultAzureCredentialBuilder().build());
        }
        return builder;
    }
}