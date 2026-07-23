package uk.gov.hmcts.cp.domain;

import java.util.List;

public record Vocabulary(
        boolean custodyLocationIsPolice,
        boolean custodyLocationIsPrison,
        boolean inCustody,
        boolean atleastOneCustodialResult,
        boolean allNonCustodialResults,
        boolean atleastOneNonCustodialResult,
        boolean cpsProsecuted,
        boolean youthDefendant,
        boolean adultDefendant,
        boolean welshCourtHearing,
        boolean englishCourtHearing,
        List<String> prosecutorMajorCreditor,
        List<String> nonProsecutorMajorCreditor) {
}