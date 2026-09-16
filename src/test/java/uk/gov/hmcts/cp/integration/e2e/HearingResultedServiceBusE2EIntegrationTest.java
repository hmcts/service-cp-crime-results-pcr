package uk.gov.hmcts.cp.integration.e2e;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.openapi.model.HearingResultedEvent;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientFactory;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "service-bus.auto-start-processors=true"
})
class HearingResultedServiceBusE2EIntegrationTest extends HearingResultedFixtureAssertions {

    private static final Duration AWAIT_PERSISTENCE = Duration.ofSeconds(15);
    private static final Duration AWAIT_POLL_INTERVAL = Duration.ofMillis(500);

    @Autowired
    private ServiceBusClientFactory clientFactory;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void afterEachPersistedRows() {
        cleanUpPersistedData();
    }

    @Test
    void twoDefendantOneApplicationHearing_should_persistAndExposeViaGetPcr_whenDeliveredViaServiceBusQueue() throws Exception {
        given_a_matching_prison_court_register_subscription();
        given_the_real_hearing_payload_is_seeded_in_redis();

        when_the_hearing_resulted_event_is_published_to_the_queue();

        await().atMost(AWAIT_PERSISTENCE)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .untilAsserted(this::then_the_case_hearing_is_persisted);
        then_the_version_is_persisted_with_defendant_pii_and_custody();
        then_the_court_application_is_persisted();
        then_the_offence_and_judicial_result_are_persisted();
        then_the_judicial_result_prompts_are_persisted();
        then_the_get_pcr_query_returns_the_persisted_result();
    }

    private void when_the_hearing_resulted_event_is_published_to_the_queue() {
        final HearingResultedEvent[] events = objectMapper.readValue(hearingResultedEvent(), HearingResultedEvent[].class);
        try (ServiceBusSenderClient sender = clientFactory.senderClient()) {
            sender.sendMessage(new ServiceBusMessage(objectMapper.writeValueAsString(events[0])));
        }
    }

    private void cleanUpPersistedData() {
        if (imprisonment != null) {
            judicialResultPromptRepository.findAll().stream()
                    .filter(p -> imprisonment.getId().equals(p.getJudicialResultId()))
                    .forEach(judicialResultPromptRepository::delete);
            judicialResultRepository.delete(imprisonment);
        }
        if (version != null) {
            offenceRepository.findAll().stream()
                    .filter(o -> version.getCpVersionPk().equals(o.getVersionPk()))
                    .forEach(offenceRepository::delete);
            courtApplicationRepository.findAll().stream()
                    .filter(a -> version.getCpVersionPk().equals(a.getVersionPk()))
                    .forEach(courtApplicationRepository::delete);
            versionRepository.delete(version);
        }
        if (caseHearing != null) {
            caseHearingRepository.delete(caseHearing);
        }
    }
}
