package uk.gov.hmcts.cp.integration;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class SpringLoggingIntegrationTest extends IntegrationTestBase {

    private final PrintStream originalStdOut = System.out;

    @AfterEach
    void afterEach() {
        System.setOut(originalStdOut);
    }

    @Test
    void springboot_test_should_log_correct_fields() throws IOException {
        MDC.put("any-mdc-field", "1234-1234");
        final ByteArrayOutputStream capturedStdOut = captureStdOut();
        log.info("spring boot test message");

        final Map<String, Object> capturedFields = new ObjectMapper().readValue(capturedStdOut.toString(StandardCharsets.UTF_8), new TypeReference<>() {
        });

        assertThat(capturedFields)
                .containsEntry("any-mdc-field", "1234-1234")
                .containsKey("timestamp")
                .containsEntry("logger_name", "uk.gov.hmcts.cp.integration.SpringLoggingIntegrationTest")
                .containsEntry("thread_name", "Test worker")
                .containsEntry("level", "INFO")
                .containsEntry("message", "spring boot test message");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }
}