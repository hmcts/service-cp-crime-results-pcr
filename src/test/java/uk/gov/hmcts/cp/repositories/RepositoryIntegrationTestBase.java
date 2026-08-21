package uk.gov.hmcts.cp.repositories;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;

// Same pattern as service-cp-crime-hearing-results-document-subscription — a real, manually-
// started Postgres via PostgresInitialise, not @DataJpaTest/Testcontainers.
@SpringBootTest
@ContextConfiguration(initializers = PostgresInitialise.class)
// These tests never exercise the Service Bus consumer — its @PostConstruct would otherwise
// block the context boot polling a nonexistent emulator.
@TestPropertySource(properties = "service-bus.auto-start-processors=false")
public abstract class RepositoryIntegrationTestBase {
}
