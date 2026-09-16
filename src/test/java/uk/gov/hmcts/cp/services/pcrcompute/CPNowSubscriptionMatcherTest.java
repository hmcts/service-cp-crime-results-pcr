package uk.gov.hmcts.cp.services.pcrcompute;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.pcrcompute.CPNowSubscription;
import uk.gov.hmcts.cp.domain.pcrcompute.CPNowSubscription.CPResultPrompt;
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
                .includedResults(List.of("22e52dce-1e58-464f-bd48-7e219747f08f"))
                .build());
        final JudicialResult result = JudicialResult.builder()
                .judicialResultTypeId("22e52dce-1e58-464f-bd48-7e219747f08f")
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenIncludedResultAbsent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .includedResults(List.of("22e52dce-1e58-464f-bd48-7e219747f08f"))
                .build());
        final JudicialResult result = JudicialResult.builder()
                .judicialResultTypeId("9999999-1e58-464f-bd48-7e219747f08f")
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isFalse();
    }

    @Test
    void matches_should_returnFalse_whenExcludedResultPresent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .excludedResults(List.of("22e52dce-1e58-464f-bd48-7e219747f08f"))
                .build());
        final JudicialResult result = JudicialResult.builder()
                .judicialResultTypeId("22e52dce-1e58-464f-bd48-7e219747f08f")
                .build();

        assertThat(matcher.matches(subscription, vocabulary(), List.of(result))).isFalse();
    }

    // AMP-1091: reference data's "YCS PCR Subscription" gates youth-detention custodial outcomes
    // (RDET/RDETO/RIYDA) via ignoreResults + includedResults keyed on judicialResultTypeId, since
    // those results never carry the prisonOrganisationName prompt atleastOneCustodialResult relies on.
    @Test
    void matches_should_returnTrue_whenIncludedResultMatchesByJudicialResultTypeId_withNoCustodialPromptAtAll() {
        final String riydaJudicialResultTypeId = "22e52dce-1e58-464f-bd48-7e219747f08f";
        final CPVocabulary noCustodialPromptVocabulary = new CPVocabulary(
                false, false, false,
                false, true, true,
                false,
                true, false,
                false, true,
                List.of(), List.of());
        final CPNowSubscription ycsPcrSubscription = subscriptionWith(SubscriptionVocabulary.builder()
                .anyAppearance(true)
                .anyCourtHearing(true)
                .adultOrYouthDefendant(true)
                .ignoreCustody(true)
                .ignoreResults(true)
                .includedResults(List.of(riydaJudicialResultTypeId, "6c535814-ea88-42da-a347-31fb6da7d851"))
                .build());
        final JudicialResult riydaResult = JudicialResult.builder()
                .judicialResultTypeId(riydaJudicialResultTypeId)
                .label("Remand in Youth Detention Accommodation")
                .judicialResultPrompts(List.of(
                        JudicialResultPrompt.builder().promptReference("remandBasis").build(),
                        JudicialResultPrompt.builder()
                                .promptReference("toBeKeptInYouthDetentionAccommodationOnTheGroundsThat")
                                .build()))
                .build();

        assertThat(matcher.matches(ycsPcrSubscription, noCustodialPromptVocabulary, List.of(riydaResult))).isTrue();
    }

    @Test
    void matches_should_returnFalse_whenNoResultMatchesYcsIncludedResultsByJudicialResultTypeId() {
        final CPVocabulary noCustodialPromptVocabulary = new CPVocabulary(
                false, false, false,
                false, true, true,
                false,
                true, false,
                false, true,
                List.of(), List.of());
        final CPNowSubscription ycsPcrSubscription = subscriptionWith(SubscriptionVocabulary.builder()
                .anyAppearance(true)
                .anyCourtHearing(true)
                .adultOrYouthDefendant(true)
                .ignoreCustody(true)
                .ignoreResults(true)
                .includedResults(List.of("22e52dce-1e58-464f-bd48-7e219747f08f"))
                .build());
        final JudicialResult unrelatedResult = JudicialResult.builder()
                .judicialResultTypeId("11111111-1111-1111-1111-111111111111")
                .build();

        assertThat(matcher.matches(ycsPcrSubscription, noCustodialPromptVocabulary, List.of(unrelatedResult))).isFalse();
    }

    @Test
    void matches_should_returnTrue_whenIncludedPromptPresent() {
        final CPNowSubscription subscription = subscriptionWith(fullyPermissiveVocabulary().toBuilder()
                .includedPrompts(List.of(CPResultPrompt.builder()
                        .resultPromptId("a9ad5002-ea38-4374-a475-4b352cdfa207")
                        .resultPromptReference("prisonOrganisationName")
                        .build()))
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
                .excludedPrompts(List.of(CPResultPrompt.builder()
                        .resultPromptId("a9ad5002-ea38-4374-a475-4b352cdfa208")
                        .resultPromptReference("prisonOrganisationName")
                        .build()))
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