package uk.gov.hmcts.cp.exceptions;

import java.util.UUID;

// Legacy's own getOrderedDate has no designed fallback (PrisonCourtRegisterSubscriptions/
// index.js:52-57) — its .find() returns undefined when nobody has an orderedDate, and the
// next line throws a TypeError, silently swallowed by the enclosing try/catch. This replicates
// that failure outcome explicitly rather than inventing a fallback date (design doc §4.2).
public class NoOrderedDateFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoOrderedDateFoundException(final UUID hearingId) {
        super("No judicial result with an orderedDate found anywhere on hearingId " + hearingId);
    }
}