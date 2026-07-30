package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.services.PcrResultsService;
import uk.gov.hmcts.cp.services.ingestion.CPEntityPersistenceService;
import uk.gov.hmcts.cp.services.ingestion.ResultsIngestionService;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

// DataSourceAutoConfiguration excluded: these full-context tests exercise the web/tracing/
// logging layers only, never persistence. Without this, adding spring-boot-starter-data-jpa
// makes Hibernate eagerly connect (both via flywayInitializer and its own dialect
// auto-detection) to application.yaml's real Postgres URL at context startup — which fails
// everywhere that database doesn't exist yet (every dev machine and CI, until phase 2
// provisions it). Excluding the DataSource bean itself cascades to back off both
// FlywayAutoConfiguration and HibernateJpaAutoConfiguration, since both require one.
@SpringBootTest
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@AutoConfigureMockMvc
@Slf4j
public abstract class IntegrationTestBase {

    @Autowired
    AppPropertiesBackend appProperties;

    @Resource
    protected MockMvc mockMvc;

    @MockitoBean
    ResultsIngestionService resultsIngestionService;

    // PcrResultsService is now repository-backed (7 JpaRepository constructor deps) — with
    // DataSourceAutoConfiguration excluded above, those beans don't exist, so PcrResultsController
    // can't be constructed for real. Mocked for the same reason resultsIngestionService is.
    @MockitoBean
    PcrResultsService pcrResultsService;

    // CPEntityPersistenceService holds the 5 repository dependencies ResultsIngestionService
    // used to hold directly — same reason as above, mocked so its real constructor never runs.
    @MockitoBean
    CPEntityPersistenceService persistenceService;

    @SneakyThrows
    protected String readResourceContents(final String resourceName) {
        final URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}
