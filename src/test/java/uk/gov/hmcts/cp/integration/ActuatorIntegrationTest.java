package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.services.PcrResultsService;
import uk.gov.hmcts.cp.services.ingestion.CPEntityPersistenceService;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "service-bus.auto-start-processors=false")
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert") // MockMvc andExpect() calls are assertions
class ActuatorIntegrationTest {

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private ResultsIngestionService resultsIngestionService;

    @MockitoBean
    private PcrResultsService pcrResultsService;

    @MockitoBean
    private CPEntityPersistenceService persistenceService;

    @Test
    void actuator_info_should_have_build_fields() throws Exception {
        final String name = "service-cp-crime-results-pcr";
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value(name))
                .andExpect(jsonPath("$.build.name").value(name))
                .andExpect(jsonPath("$.build.time").exists())
                .andExpect(jsonPath("$.build.version").exists());
    }

    @Test
    void actuator_info_should_have_gorylenko_git_fields() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.git.branch").exists())
                .andExpect(jsonPath("$.git.commit.id").exists())
                .andExpect(jsonPath("$.git.commit.time").exists());
    }

    @Test
    void actuator_health_should_have_correct_fields() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.groups[0]").value("liveness"))
                .andExpect(jsonPath("$.groups[1]").value("readiness"));
    }
}
