package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class NowSubscription {

    private boolean isPrisonCourtRegisterSubscription;
    // false, or subscriptionVocabulary absent -> matches by default (checkIfCustodyMatch et al
    // are never consulted) — the one place "unconfigured means pass" is correct; every
    // dimension inside SubscriptionVocabulary is fail-closed when unconfigured instead.
    private boolean applySubscriptionRules;
    private SubscriptionVocabulary subscriptionVocabulary;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder(toBuilder = true)
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class SubscriptionVocabulary {

        // CPS short-circuit — bypasses every other dimension below once both this and the
        // defendant's own Vocabulary.cpsProsecuted are true (checkIfCpsProsecuted).
        private boolean isCpsProsecuted;

        // Attendance — matched against SubscriptionsService.js:240-256's checkIfAttendanceTypeMatch,
        // but Vocabulary has no real appearedInPerson/appearedByVideoLink source yet (design doc §7 —
        // hearing.defendantAttendance is unconfirmed by fixture). anyAppearance still bypasses per
        // real behaviour; a specific requirement without it can never be satisfied today.
        private boolean anyAppearance;
        private boolean appearedInPerson;
        private boolean appearedByVideoLink;

        // Vocabulary.prosecutorMajorCreditor/nonProsecutorMajorCreditor are always empty,
        // non-null lists for PCR (design doc §2) — requiresProsecutorMajorCreditor/
        // requiresNonProsecutorMajorCreditor can therefore never be satisfied on their own;
        // only anyMajorCreditor genuinely defaults to pass (checkIfMajorCreditorTypeMatch).
        private boolean anyMajorCreditor;
        private boolean requiresProsecutorMajorCreditor;
        private boolean requiresNonProsecutorMajorCreditor;

        // Court language — same rule as checkIfCourtHouseMatch (SubscriptionsService.js:313-326).
        private boolean anyCourtHearing;
        private boolean englishCourtHearing;
        private boolean welshCourtHearing;

        // Age group — checkIfDefendantMatch (SubscriptionsService.js:328-341).
        private boolean adultOrYouthDefendant;
        private boolean youthDefendant;
        private boolean adultDefendant;

        // Custody — checkIfCustodyMatch (SubscriptionsService.js:343-365).
        private boolean ignoreCustody;
        private boolean inCustody;
        private boolean custodyLocationIsPolice;
        private boolean custodyLocationIsPrison;

        // Custodial outcome — checkIfCustodialResultMatch (SubscriptionsService.js:367-380).
        private boolean ignoreResults;
        private boolean allNonCustodialResults;
        private boolean atleastOneCustodialResult;
        private boolean atleastOneNonCustodialResult;

        // Prompt/result include-exclude lists — matched by exact promptReference/cjsCode value
        // (SubscriptionsService.js:212-238's NAMEADDRESS substring-match nuance is not modelled).
        private List<String> includedPrompts;
        private List<String> excludedPrompts;
        private List<String> includedResults;
        private List<String> excludedResults;
    }
}