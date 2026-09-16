package uk.gov.hmcts.cp.servicebus.services;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
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

    public ServiceBusReceiverClient deadLetterReceiverClient() {
        return clientBuilder().receiver()
                .queueName(ServiceBusProperties.QUEUE_NAME)
                .subQueue(SubQueue.DEAD_LETTER_QUEUE)
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                .buildClient();
    }

    private ServiceBusClientBuilder clientBuilder() {
        return properties.isEmulator()
                ? new ServiceBusClientBuilder().connectionString(properties.getConnectionString())
                : new ServiceBusClientBuilder()
                        .fullyQualifiedNamespace(URI.create(properties.getConnectionString()).getHost())
                        .credential(new DefaultAzureCredentialBuilder().build());
    }
}
