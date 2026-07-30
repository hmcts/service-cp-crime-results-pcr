package uk.gov.hmcts.cp.integration.e2e;

import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.integration.config.PostgresInitialise;
import uk.gov.hmcts.cp.integration.config.RedisInitialise;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {PostgresInitialise.class, RedisInitialise.class})
public abstract class IngestionE2ETestBase {

    @Resource
    protected MockMvc mockMvc;
}