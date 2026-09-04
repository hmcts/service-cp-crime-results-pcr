package uk.gov.hmcts.cp.exceptions;

import java.util.UUID;

// A hearing can have no orderedDate anywhere — CP Azure Legal Aid Agency swallows this silently; we raise it instead.
public class NoOrderedDateFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoOrderedDateFoundException(final UUID hearingId) {
        super("No judicial result with an orderedDate found anywhere on hearingId " + hearingId);
    }
}