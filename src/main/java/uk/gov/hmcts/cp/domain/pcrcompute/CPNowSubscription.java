package uk.gov.hmcts.cp.domain.pcrcompute;

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
public class CPNowSubscription {

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

        // Every field below is Boolean, not boolean — the confirmed real fixture
        // (reference-data-service-result-1-3-true.json) only includes the dimensions a given
        // subscription actually configures; a PCR subscription with no major-creditor or
        // custody-ignore requirement simply omits those keys rather than sending false.
        // Boxed null is treated as "not configured" (unset), same fail-closed default as an
        // explicit false, via the isTrue()/isSet() helpers in CPNowSubscriptionMatcher.

        // CPS short-circuit — bypasses every other dimension below once both this and the
        // defendant's own CPVocabulary.cpsProsecuted are true (checkIfCpsProsecuted).
        private Boolean isCpsProsecuted;

        // Attendance — matched against SubscriptionsService.js:240-256's checkIfAttendanceTypeMatch,
        // but CPVocabulary has no real appearedInPerson/appearedByVideoLink source yet (design doc §7 —
        // hearing.defendantAttendance is unconfirmed by fixture). anyAppearance still bypasses per
        // real behaviour; a specific requirement without it can never be satisfied today.
        private Boolean anyAppearance;
        private Boolean appearedInPerson;
        private Boolean appearedByVideoLink;

        // CPVocabulary.prosecutorMajorCreditor/nonProsecutorMajorCreditor are always empty,
        // non-null lists for PCR (design doc §2) — requiresProsecutorMajorCreditor/
        // requiresNonProsecutorMajorCreditor can therefore never be satisfied on their own;
        // only anyMajorCreditor genuinely defaults to pass (checkIfMajorCreditorTypeMatch).
        private Boolean anyMajorCreditor;
        private Boolean requiresProsecutorMajorCreditor;
        private Boolean requiresNonProsecutorMajorCreditor;

        // Court language — same rule as checkIfCourtHouseMatch (SubscriptionsService.js:313-326).
        private Boolean anyCourtHearing;
        private Boolean englishCourtHearing;
        private Boolean welshCourtHearing;

        // Age group — checkIfDefendantMatch (SubscriptionsService.js:328-341).
        private Boolean adultOrYouthDefendant;
        private Boolean youthDefendant;
        private Boolean adultDefendant;

        // Custody — checkIfCustodyMatch (SubscriptionsService.js:343-365).
        private Boolean ignoreCustody;
        private Boolean inCustody;
        private Boolean custodyLocationIsPolice;
        private Boolean custodyLocationIsPrison;

        // Custodial outcome — checkIfCustodialResultMatch (SubscriptionsService.js:367-380).
        private Boolean ignoreResults;
        private Boolean allNonCustodialResults;
        private Boolean atleastOneCustodialResult;
        private Boolean atleastOneNonCustodialResult;

        // Prompt/result include-exclude lists — matched by exact promptReference/cjsCode value
        // (SubscriptionsService.js:212-238's NAMEADDRESS substring-match nuance is not modelled).
        private List<String> includedPrompts;
        private List<String> excludedPrompts;
        private List<String> includedResults;
        private List<String> excludedResults;
    }
}