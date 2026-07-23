package uk.gov.hmcts.cp.filters.tracing;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@AllArgsConstructor
public class TracingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_KEY = "X-Correlation-Id";

    private final UUIDService uuidService;

    @SneakyThrows
    @Override
    protected boolean shouldNotFilter(@Nonnull final HttpServletRequest request) {
        final String pathRoot = new URI(request.getRequestURI()).getPath();
        return "/".equals(pathRoot) || pathRoot.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request,
                                    @Nonnull final HttpServletResponse response,
                                    @Nonnull final FilterChain filterChain) throws ServletException, IOException {
        try {
            final String correlationId = getCorrelationId(request);
            MDC.put(CORRELATION_ID_KEY, correlationId);
            response.setHeader(CORRELATION_ID_KEY, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private String getCorrelationId(final HttpServletRequest request) {
        final String existingId = request.getHeader(CORRELATION_ID_KEY);
        final String correlationId;
        if (existingId == null) {
            log.info("No 'X-Correlation-Id' header found. Generating Correlation ID.");
            correlationId = uuidService.randomString();
        } else {
            correlationId = existingId;
        }
        return correlationId;
    }
}