package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
public abstract class IntegrationTestBase {

    @Autowired
    AppPropertiesBackend appProperties;

    @Resource
    protected MockMvc mockMvc;

    @SneakyThrows
    protected String readResourceContents(final String resourceName) {
        final URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}