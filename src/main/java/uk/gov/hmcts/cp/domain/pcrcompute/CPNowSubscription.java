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
    private boolean applySubscriptionRules;
    private SubscriptionVocabulary subscriptionVocabulary;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder(toBuilder = true)
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class SubscriptionVocabulary {

        // Boolean not boolean — a subscription omits keys it doesn't configure; null means
        // "not configured" (fail-closed), via isTrue()/isSet() in CPNowSubscriptionMatcher.

        // CPS short-circuit — bypasses every other dimension once this and cpsProsecuted are both true.
        private Boolean isCpsProsecuted;

        // CPVocabulary has no real appearedInPerson/appearedByVideoLink source yet — those can never be satisfied.
        private Boolean anyAppearance;
        private Boolean appearedInPerson;
        private Boolean appearedByVideoLink;

        // prosecutorMajorCreditor/nonProsecutorMajorCreditor are always empty for PCR — only anyMajorCreditor can pass.
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