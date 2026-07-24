package uk.gov.hmcts.cp.exceptions;

import java.util.UUID;

public class IncompleteHearingDetailsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IncompleteHearingDetailsException(final UUID hearingId) {
        super("Hearing details not yet complete for hearingId " + hearingId);
    }
}