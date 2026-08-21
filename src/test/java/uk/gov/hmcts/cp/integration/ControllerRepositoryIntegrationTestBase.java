package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;

// Real Postgres + real MockMvc, unlike IntegrationTestBase (which excludes
// DataSourceAutoConfiguration so non-persistence tests never need Postgres) — for controller
// tests that need to exercise the real controller -> service -> repository stack against
// seeded data, per service-shared.md's <Controller>IntegrationTest convention.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresInitialise.class)
// These tests never exercise the Service Bus consumer — see IntegrationTestBase for why this
// must be off, otherwise its @PostConstruct blocks the context boot polling a nonexistent emulator.
@TestPropertySource(properties = "service-bus.auto-start-processors=false")
public abstract class ControllerRepositoryIntegrationTestBase {

    @Resource
    protected MockMvc mockMvc;
}
