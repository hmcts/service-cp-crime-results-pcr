package uk.gov.hmcts.cp.services.pcrcompute;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.pcrcompute.CPNowSubscription;
import uk.gov.hmcts.cp.domain.pcrcompute.CPNowSubscription.SubscriptionVocabulary;
import uk.gov.hmcts.cp.domain.pcrcompute.CPVocabulary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CPNowSubscriptionMatcherTest {

    private final CPNowSubscriptionMatcher matcher = new CPNowSubscriptionMatcher();

    private static CPVocabulary vocabulary() {
        return new CPVocabulary(
                false, false, false,
                false, false, false,
                false,
                false, true,
                false, true,
                List.of(), List.of());
    }

    @Test
    void matches_should_returnTrue_whenApplySubscriptionRulesFalse() {
        final CPNowSubscription subscription = CPNowSubscription.builder()
                .applySubscriptionRules(false)
                .subscriptionVocabulary(SubscriptionVocabulary.builder().build())
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnTrue_whenSubscriptionVocabularyAbsent() {
        final CPNowSubscription subscription = CPNowSubscription.builder()
                .applySubscriptionRules(true)
                .subscriptionVocabulary(null)
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnTrue_whenCpsShortCircuitSatisfied() {
        final CPVocabulary vocabulary = new CPVocabulary(
                false, false, false,
                false, false, false,
                true,
                false, true,
                false, true,
                List.of(), List.of());
        final CPNowSubscription subscription = subscriptionWith(SubscriptionVocabulary.builder()
                .isCpsProsecuted(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary, List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenNoDimensionConfiguredAndCpsNotSatisfied() {
        final CPNowSubscription subscription = subscriptionWith(SubscriptionVocabulary.builder().build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenAnyAppearanceSet() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .anyAppearance(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenSpecificAttendanceRequiredWithoutAnyAppearance() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .anyAppearance(false)
                .appearedInPerson(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenAnyMajorCreditorSet() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .anyMajorCreditor(true)
                .requiresProsecutorMajorCreditor(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenProsecutorMajorCreditorRequiredWithoutAnyMajorCreditor() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .anyMajorCreditor(false)
                .requiresProsecutorMajorCreditor(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenEnglishCourtHearingMatches() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .anyCourtHearing(false)
                .englishCourtHearing(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenWelshRequiredButHearingIsEnglish() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .anyCourtHearing(false)
                .englishCourtHearing(false)
                .welshCourtHearing(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenAdultDefendantMatches() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .adultOrYouthDefendant(false)
                .adultDefendant(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenYouthRequiredButDefendantIsAdult() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .adultOrYouthDefendant(false)
                .adultDefendant(false)
                .youthDefendant(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenIgnoreCustodySet() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreCustody(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenInCustodyRequiredButVocabularyNotInCustody() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreCustody(false)
                .inCustody(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenPoliceCustodyLocationMatches() {
        final CPVocabulary vocabulary = new CPVocabulary(
                true, false, true,
                false, false, false,
                false,
                false, true,
                false, true,
                List.of(), List.of());
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreCustody(false)
                .inCustody(true)
                .custodyLocationIsPolice(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary, List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenNoCustodyRequirementAndNotIgnored() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreCustody(false)
                .inCustody(false)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenIgnoreResultsSet() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreResults(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isTrue();
    }

    @Test
    void matches_should_returnTrue_whenAtleastOneNonCustodialResultMatches() {
        final CPVocabulary vocabulary = new CPVocabulary(
                false, false, false,
                true, false, true,
                false,
                false, true,
                false, true,
                List.of(), List.of());
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreResults(false)
                .allNonCustodialResults(false)
                .atleastOneNonCustodialResult(true)
                .atleastOneCustodialResult(true)
                .build());

        assertThat(matcher.matches(subscription, vocabulary, List.of())).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenCustodialOutcomeRequirementNotMet() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .ignoreResults(false)
                .allNonCustodialResults(false)
                .atleastOneNonCustodialResult(false)
                .build());

        assertThat(matcher.matches(subscription, vocabulary(), List.of())).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenIncludedResultPresent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .includedResults(List.of("1200"))
                .build());
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenIncludedResultAbsent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .includedResults(List.of("1200"))
                .build());
        final JudicialResult result = JudicialResult.builder().cjsCode("9999").build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isFalse();
    }

    @Test
    void matches_should_returnFalse_whenExcludedResultPresent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .excludedResults(List.of("1200"))
                .build());
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenIncludedPromptPresent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .includedPrompts(List.of("prisonOrganisationName"))
                .build());
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200")
                .judicialResultPrompts(List.of(
                        JudicialResultPrompt.builder().promptReference("prisonOrganisationName").build()))
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenExcludedPromptPresent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .excludedPrompts(List.of("prisonOrganisationName"))
                .build());
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200")
                .judicialResultPrompts(List.of(
                        JudicialResultPrompt.builder().promptReference("prisonOrganisationName").build()))
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isFalse();
    }

    private static CPNowSubscription subscriptionWith(final SubscriptionVocabulary subscriptionVocabulary) {
        return CPNowSubscription.builder()
                .applySubscriptionRules(true)
                .subscriptionVocabulary(subscriptionVocabulary)
                .build();
    }

    private static SubscriptionVocabulary fullyPermissiveVocabulary() {
        return SubscriptionVocabulary.builder()
                .anyAppearance(true)
                .anyMajorCreditor(true)
                .anyCourtHearing(true)
                .adultOrYouthDefendant(true)
                .ignoreCustody(true)
                .ignoreResults(true)
                .build();
    }
}