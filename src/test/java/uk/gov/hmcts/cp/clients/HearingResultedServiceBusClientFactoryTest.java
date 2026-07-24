package uk.gov.hmcts.cp.clients;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.config.ServiceBusProperties;

import static org.assertj.core.api.Assertions.assertThat;

class HearingResultedServiceBusClientFactoryTest {

    private static final String EMULATOR_ADMIN = "Endpoint=sb://localhost:5300;SharedAccessKeyName=key;SharedAccessKey=key;UseDevelopmentEmulator=true;";
    private static final String EMULATOR_CONNECTION = "Endpoint=sb://localhost;SharedAccessKeyName=key;SharedAccessKey=key;UseDevelopmentEmulator=true;";
    private static final String AZURE_CONNECTION = "https://pcr-ns.servicebus.windows.net";
    private static final String QUEUE_NAME = "pcr.hearing-resulted";

    @Test
    void senderClient_should_buildNonNullClient_forEmulatorConnection() {
        final HearingResultedServiceBusClientFactory factory = factoryWith(EMULATOR_CONNECTION);
        try (ServiceBusSenderClient sender = factory.senderClient()) {
            assertThat(sender).isNotNull();
        }
    }

    @Test
    void senderClient_should_buildNonNullClient_forAzureConnection() {
        final HearingResultedServiceBusClientFactory factory = factoryWith(AZURE_CONNECTION);
        try (ServiceBusSenderClient sender = factory.senderClient()) {
            assertThat(sender).isNotNull();
        }
    }

    @Test
    void processorClient_should_buildNonNullClient_forEmulatorConnection() {
        final HearingResultedServiceBusClientFactory factory = factoryWith(EMULATOR_CONNECTION);
        try (ServiceBusProcessorClient processor = factory.processorClient(context -> { }, error -> { })) {
            assertThat(processor).isNotNull();
        }
    }

    private HearingResultedServiceBusClientFactory factoryWith(final String connection) {
        final ServiceBusProperties properties = new ServiceBusProperties(EMULATOR_ADMIN, connection, QUEUE_NAME);
        return new HearingResultedServiceBusClientFactory(properties);
    }
}