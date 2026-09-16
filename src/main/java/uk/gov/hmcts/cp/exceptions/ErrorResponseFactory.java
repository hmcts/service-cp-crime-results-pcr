package uk.gov.hmcts.cp.exceptions;

import io.micrometer.tracing.Tracer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;
import uk.gov.hmcts.cp.services.ClockService;

import java.util.Objects;

@Service
@AllArgsConstructor
public class ErrorResponseFactory {

    private final Tracer tracer;
    private final ClockService clockService;

    public ErrorResponse build(final String message) {
        return build(message, null);
    }

    public ErrorResponse build(final String message, final String errorCode) {
        return ErrorResponse.builder()
                .message(message)
                .error(errorCode)
                .timestamp(clockService.now())
                .traceId(Objects.requireNonNull(tracer.currentSpan()).context().traceId())
                .build();
    }
}
