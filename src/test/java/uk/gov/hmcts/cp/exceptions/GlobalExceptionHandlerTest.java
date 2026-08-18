package uk.gov.hmcts.cp.exceptions;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private Tracer tracer;
    @Mock
    private Span span;
    @Mock
    private TraceContext traceContext;
    @Spy
    private ClockService clockService =
            new ClockService(Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC));

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleIncompleteHearingDetails_should_return503_withWarnLog() {
        stubTracer();
        final IncompleteHearingDetailsException exception =
                new IncompleteHearingDetailsException(UUID.fromString("00000000-0000-0000-0000-000000000011"));

        final ResponseEntity<ErrorResponse> response = handler.handleIncompleteHearingDetails(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
    }

    @Test
    void handleMalformedEventPayload_should_return400() {
        stubTracer();
        final IllegalArgumentException exception = new IllegalArgumentException("Unrecognized eventType: bogus");

        final ResponseEntity<ErrorResponse> response = handler.handleMalformedEventPayload(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
    }

    private void stubTracer() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("b2f1c3d4e5f60718");
    }
}
