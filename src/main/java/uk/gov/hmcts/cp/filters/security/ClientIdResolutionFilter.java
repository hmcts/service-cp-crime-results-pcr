package uk.gov.hmcts.cp.filters.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.auth.AuthMode;
import uk.gov.hmcts.cp.auth.AuthorizationPolicy;
import uk.gov.hmcts.cp.auth.EntraAuthProperties;
import uk.gov.hmcts.cp.auth.EntraTokenValidator;
import uk.gov.hmcts.cp.auth.TokenValidationException;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;
import uk.gov.hmcts.cp.auth.ValidatedCaller;
import uk.gov.hmcts.cp.exceptions.ErrorResponseFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Resolves and, per {@link AuthMode}, enforces the caller identity from the bearer token on
 * every non-exempt request. Runs after {@link uk.gov.hmcts.cp.filters.tracing.TracingFilter} so
 * the correlation id is already in MDC for a rejection log line. Rejections are written here,
 * not via {@code GlobalExceptionHandler} — this filter runs before Spring MVC dispatch, so
 * {@code @RestControllerAdvice} never sees them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
@AllArgsConstructor
public class ClientIdResolutionFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTRIBUTE = "uk.gov.hmcts.cp.auth.CALLER";
    private static final String OBSERVED_FAILURE_METRIC = "cp.auth.observed.failure";
    private static final ValidatedCaller UNVERIFIED_CALLER = new ValidatedCaller(null, List.of(), false);

    private final EntraAuthProperties properties;
    private final EntraTokenValidator tokenValidator;
    private final AuthorizationPolicy authorizationPolicy;
    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @SneakyThrows
    @Override
    protected boolean shouldNotFilter(@Nonnull final HttpServletRequest request) {
        return authorizationPolicy.isExempt(new URI(request.getRequestURI()).getPath());
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request,
                                     @Nonnull final HttpServletResponse response,
                                     @Nonnull final FilterChain filterChain) throws ServletException, IOException {
        try {
            request.setAttribute(CALLER_ATTRIBUTE, resolveCaller(request));
        } catch (TokenValidationException e) {
            writeRejection(request, response, e.getReason());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private ValidatedCaller resolveCaller(final HttpServletRequest request) throws TokenValidationException {
        final ValidatedCaller caller;
        if (properties.getMode() == AuthMode.OFF) {
            caller = UNVERIFIED_CALLER;
        } else {
            caller = validateOrFallback(request);
        }
        return caller;
    }

    private ValidatedCaller validateOrFallback(final HttpServletRequest request) throws TokenValidationException {
        ValidatedCaller caller;
        try {
            caller = tokenValidator.validate(extractBearerToken(request));
        } catch (TokenValidationException e) {
            if (properties.getMode() != AuthMode.OBSERVE) {
                throw e;
            }
            meterRegistry.counter(OBSERVED_FAILURE_METRIC, "reason", e.getReason().name()).increment();
            caller = UNVERIFIED_CALLER;
        }
        return caller;
    }

    private String extractBearerToken(final HttpServletRequest request) throws TokenValidationException {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            throw new TokenValidationException(Reason.MISSING_HEADER);
        }
        final int spaceIndex = header.indexOf(' ');
        final String token = spaceIndex < 0 ? "" : header.substring(spaceIndex + 1).trim();
        if (spaceIndex < 0 || !"Bearer".equalsIgnoreCase(header.substring(0, spaceIndex)) || token.isEmpty()) {
            throw new TokenValidationException(Reason.MISSING_HEADER);
        }
        return token;
    }

    private void writeRejection(final HttpServletRequest request, final HttpServletResponse response, final Reason reason)
            throws IOException {
        log.warn("ClientIdResolutionFilter rejected request to {}: {}", sanitizeForLog(request.getRequestURI()), reason);
        response.setStatus(reason.isAuthenticationFailure()
                ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.FORBIDDEN.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(reason));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponseFactory.build(reason.description(), reason.errorCode()));
    }

    private String sanitizeForLog(final String value) {
        return value == null ? null : value.replace('\n', '_').replace('\r', '_');
    }

    /** Bare challenge when no usable credential was presented at all; a detailed RFC 6750
     * challenge once a token was actually extracted and evaluated. */
    private String challenge(final Reason reason) {
        final String value;
        if (reason == Reason.MISSING_HEADER) {
            value = "Bearer";
        } else {
            value = "Bearer error=\"" + reason.errorCode() + "\", error_description=\"" + reason.description() + "\"";
        }
        return value;
    }
}
