package uk.gov.hmcts.cp.domain.orchestrator;

import lombok.Builder;

import java.util.List;

@Builder
public record CPVocabulary(
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