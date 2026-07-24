package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceBusPropertiesTest {

    @Test
    void isEmulator_should_returnTrue_whenConnectionIsEmulatorStyle() {
        final ServiceBusProperties properties = new ServiceBusProperties(
                "Endpoint=sb://localhost:5300;UseDevelopmentEmulator=true;",
                "Endpoint=sb://localhost;UseDevelopmentEmulator=true;",
                "pcr.hearing-resulted");

        assertThat(properties.isEmulator()).isTrue();
    }

    @Test
    void isEmulator_should_returnFalse_whenConnectionIsAzureHttps() {
        final ServiceBusProperties properties = new ServiceBusProperties(
                "https://pcr-ns.servicebus.windows.net",
                "https://pcr-ns.servicebus.windows.net",
                "pcr.hearing-resulted");

        assertThat(properties.isEmulator()).isFalse();
    }
}