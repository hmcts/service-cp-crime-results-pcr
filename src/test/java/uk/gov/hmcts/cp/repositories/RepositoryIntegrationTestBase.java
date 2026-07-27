package uk.gov.hmcts.cp.repositories;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;

// Same pattern as service-cp-crime-hearing-results-document-subscription — a real, manually-
// started Postgres via PostgresInitialise, not @DataJpaTest/Testcontainers.
@SpringBootTest
@ContextConfiguration(initializers = PostgresInitialise.class)
public abstract class RepositoryIntegrationTestBase {
}
