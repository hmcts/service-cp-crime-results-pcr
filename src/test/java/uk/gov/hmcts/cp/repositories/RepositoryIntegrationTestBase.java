package uk.gov.hmcts.cp.repositories;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;

@SpringBootTest
@ContextConfiguration(initializers = PostgresInitialise.class)
@TestPropertySource(properties = "service-bus.auto-start-processors=false")
public abstract class RepositoryIntegrationTestBase {
}
