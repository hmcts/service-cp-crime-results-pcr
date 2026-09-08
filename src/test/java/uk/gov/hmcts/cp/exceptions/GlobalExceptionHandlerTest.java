package uk.gov.hmcts.cp.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private ErrorResponseFactory errorResponseFactory;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleIncompleteHearingDetails_should_return503_withWarnLog() {
        final IncompleteHearingDetailsException exception =
                new IncompleteHearingDetailsException(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        stubFactory(exception.getMessage());

        final ResponseEntity<ErrorResponse> response = handler.handleIncompleteHearingDetails(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
    }

    @Test
    void handleMalformedEventPayload_should_return400() {
        final IllegalArgumentException exception = new IllegalArgumentException("Unrecognized eventType: bogus");
        stubFactory(exception.getMessage());

        final ResponseEntity<ErrorResponse> response = handler.handleMalformedEventPayload(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
    }

    private void stubFactory(final String message) {
        when(errorResponseFactory.build(anyString()))
                .thenReturn(ErrorResponse.builder().message(message).build());
    }
}
