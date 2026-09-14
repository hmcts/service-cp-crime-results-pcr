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
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorResponseFactoryTest {

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
    private ErrorResponseFactory factory;

    @Test
    void build_should_setMessageTimestampAndTraceId() {
        stubTracer();

        final ErrorResponse response = factory.build("something went wrong");

        assertThat(response.getMessage()).isEqualTo("something went wrong");
        assertThat(response.getTimestamp()).isEqualTo(Instant.parse("2026-07-28T10:00:00Z"));
        assertThat(response.getTraceId()).isEqualTo("b2f1c3d4e5f60718");
        assertThat(response.getError()).isNull();
    }

    @Test
    void build_withErrorCode_should_alsoSetErrorField() {
        stubTracer();

        final ErrorResponse response = factory.build("token rejected", "invalid_token");

        assertThat(response.getMessage()).isEqualTo("token rejected");
        assertThat(response.getError()).isEqualTo("invalid_token");
    }

    private void stubTracer() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("b2f1c3d4e5f60718");
    }
}
