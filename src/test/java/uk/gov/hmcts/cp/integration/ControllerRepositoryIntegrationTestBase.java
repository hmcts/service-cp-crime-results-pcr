package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresInitialise.class)
@TestPropertySource(properties = "service-bus.auto-start-processors=false")
public abstract class ControllerRepositoryIntegrationTestBase {

    @Resource
    protected MockMvc mockMvc;
}
