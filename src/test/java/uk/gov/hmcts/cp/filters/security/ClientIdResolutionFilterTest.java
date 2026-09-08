package uk.gov.hmcts.cp.filters.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.auth.AuthMode;
import uk.gov.hmcts.cp.auth.AuthorizationPolicy;
import uk.gov.hmcts.cp.auth.EntraAuthProperties;
import uk.gov.hmcts.cp.auth.EntraTokenValidator;
import uk.gov.hmcts.cp.auth.TokenValidationException;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;
import uk.gov.hmcts.cp.auth.ValidatedCaller;
import uk.gov.hmcts.cp.exceptions.ErrorResponseFactory;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIdResolutionFilterTest {

    private static final String PROTECTED_PATH = "/cases/URN123/hearings/1/defendants/2";
    private static final ValidatedCaller VERIFIED_CALLER =
            new ValidatedCaller(UUID.fromString("33333333-3333-3333-3333-333333333333"), List.of("PcrReader"), true);

    @Mock
    private EntraAuthProperties properties;
    @Mock
    private EntraTokenValidator tokenValidator;
    @Mock
    private AuthorizationPolicy authorizationPolicy;
    @Mock
    private ErrorResponseFactory errorResponseFactory;
    @Mock
    private FilterChain filterChain;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private ClientIdResolutionFilter filter;

    @Test
    void shouldNotFilter_should_delegateToAuthorizationPolicy() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        when(authorizationPolicy.isExempt("/actuator/health")).thenReturn(true);

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void doFilterInternal_should_continueChain_whenTokenValid() throws Exception {
        lenient().when(properties.getMode()).thenReturn(AuthMode.ENFORCE);
        when(tokenValidator.validate("a-valid-token")).thenReturn(VERIFIED_CALLER);
        final MockHttpServletRequest request = protectedRequest("Bearer a-valid-token");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(request.getAttribute(ClientIdResolutionFilter.CALLER_ATTRIBUTE)).isEqualTo(VERIFIED_CALLER);
    }

    @Test
    void doFilterInternal_should_matchBearerSchemeCaseInsensitively() throws Exception {
        lenient().when(properties.getMode()).thenReturn(AuthMode.ENFORCE);
        when(tokenValidator.validate("a-valid-token")).thenReturn(VERIFIED_CALLER);
        final MockHttpServletRequest request = protectedRequest("bearer a-valid-token");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Token abc", "Bearer", "Bearer "})
    void doFilterInternal_should_reject401_whenAuthorizationHeaderMissingOrMalformed(final String headerValue) throws Exception {
        lenient().when(properties.getMode()).thenReturn(AuthMode.ENFORCE);
        final MockHttpServletRequest request = protectedRequest(headerValue);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        when(errorResponseFactory.build(Reason.MISSING_HEADER.description(), Reason.MISSING_HEADER.errorCode()))
                .thenReturn(ErrorResponse.builder().message(Reason.MISSING_HEADER.description()).build());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void doFilterInternal_should_reject401_whenNoAuthorizationHeaderAtAll() throws Exception {
        lenient().when(properties.getMode()).thenReturn(AuthMode.ENFORCE);
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        when(errorResponseFactory.build(Reason.MISSING_HEADER.description(), Reason.MISSING_HEADER.errorCode()))
                .thenReturn(ErrorResponse.builder().message(Reason.MISSING_HEADER.description()).build());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void doFilterInternal_should_reject401_withDetailedChallenge_whenHeaderPresentButTokenInvalid() throws Exception {
        lenient().when(properties.getMode()).thenReturn(AuthMode.ENFORCE);
        final MockHttpServletRequest request = protectedRequest("Bearer bad-token");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenValidator.validate("bad-token")).thenThrow(new TokenValidationException(Reason.INVALID_SIGNATURE));
        when(errorResponseFactory.build(Reason.INVALID_SIGNATURE.description(), Reason.INVALID_SIGNATURE.errorCode()))
                .thenReturn(ErrorResponse.builder().message(Reason.INVALID_SIGNATURE.description()).build());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\", error_description=\"Token signature is invalid\"");
    }

    @Test
    void doFilterInternal_should_reject403_whenReasonIsNotAnAuthenticationFailure() throws Exception {
        lenient().when(properties.getMode()).thenReturn(AuthMode.ENFORCE);
        final MockHttpServletRequest request = protectedRequest("Bearer delegated-token");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenValidator.validate("delegated-token")).thenThrow(new TokenValidationException(Reason.DELEGATED_TOKEN));
        when(errorResponseFactory.build(Reason.DELEGATED_TOKEN.description(), Reason.DELEGATED_TOKEN.errorCode()))
                .thenReturn(ErrorResponse.builder().message(Reason.DELEGATED_TOKEN.description()).build());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void doFilterInternal_should_skipValidationEntirely_whenModeOff() throws Exception {
        when(properties.getMode()).thenReturn(AuthMode.OFF);
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tokenValidator, never()).validate(any());
        final ValidatedCaller caller = (ValidatedCaller) request.getAttribute(ClientIdResolutionFilter.CALLER_ATTRIBUTE);
        assertThat(caller.verified()).isFalse();
    }

    @Test
    void doFilterInternal_should_notReject_whenModeObserve_andValidationFails() throws Exception {
        when(properties.getMode()).thenReturn(AuthMode.OBSERVE);
        final MockHttpServletRequest request = protectedRequest("Bearer bad-token");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenValidator.validate("bad-token")).thenThrow(new TokenValidationException(Reason.EXPIRED));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(meterRegistry.get("cp.auth.observed.failure").tag("reason", "EXPIRED").counter().count())
                .isEqualTo(1.0);
        final ValidatedCaller caller = (ValidatedCaller) request.getAttribute(ClientIdResolutionFilter.CALLER_ATTRIBUTE);
        assertThat(caller.verified()).isFalse();
    }

    private static MockHttpServletRequest protectedRequest(final String authorizationHeader) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        request.addHeader("Authorization", authorizationHeader);
        return request;
    }
}
