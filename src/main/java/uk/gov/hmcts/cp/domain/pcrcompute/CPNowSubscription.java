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
    // false, or subscriptionVocabulary absent -> matches by default. The one "unconfigured means pass" case; every dimension inside SubscriptionVocabulary fails closed instead.
    private boolean applySubscriptionRules;
    private SubscriptionVocabulary subscriptionVocabulary;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder(toBuilder = true)
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class SubscriptionVocabulary {

        // Every field below is Boolean, not boolean — a subscription omits keys it doesn't
        // configure rather than sending false. Boxed null means "not configured" (fail-closed),
        // via isTrue()/isSet() in CPNowSubscriptionMatcher.

        // CPS short-circuit — bypasses every other dimension once both this and the defendant's own cpsProsecuted are true.
        private Boolean isCpsProsecuted;

        // Attendance — CPVocabulary has no real appearedInPerson/appearedByVideoLink source yet.
        // anyAppearance still bypasses; a specific requirement without it can never be satisfied today.
        private Boolean anyAppearance;
        private Boolean appearedInPerson;
        private Boolean appearedByVideoLink;

        // prosecutorMajorCreditor/nonProsecutorMajorCreditor are always empty for PCR, so
        // requiresProsecutorMajorCreditor/requiresNonProsecutorMajorCreditor can never be satisfied alone; only anyMajorCreditor defaults to pass.
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

        // Prompt/result include-exclude lists — matched by exact promptReference/cjsCode value.
        // Prompts are objects on the real API, not bare strings — matched on resultPromptReference.
        private List<CPResultPrompt> includedPrompts;
        private List<CPResultPrompt> excludedPrompts;
        private List<String> includedResults;
        private List<String> excludedResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CPResultPrompt {

        private String resultPromptId;
        private String resultPromptReference;
    }
}