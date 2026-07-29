package uk.gov.hmcts.cp.integration.e2e;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;
import uk.gov.hmcts.cp.integration.config.RedisInitialise;

// Real Postgres + real Redis, matching this repo's established convention (PostgresInitialise) —
// Testcontainers was tried for Postgres and deliberately abandoned in favour of this approach,
// so Redis follows the same pattern rather than introducing Testcontainers for one dependency.
@SpringBootTest
@ContextConfiguration(initializers = {PostgresInitialise.class, RedisInitialise.class})
public abstract class IngestionE2ETestBase {
}