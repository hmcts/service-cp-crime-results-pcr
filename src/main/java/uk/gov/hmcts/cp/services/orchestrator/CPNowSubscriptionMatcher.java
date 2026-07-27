package uk.gov.hmcts.cp.services.orchestrator;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscription.SubscriptionVocabulary;
import uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary;

import java.util.List;

// Matches design doc §4 / SubscriptionsService.js's matchVocabularyRules — PCR subscriptions
// (isPrisonCourtRegisterSubscription) are matched by this alone, no court-house/prosecutor/
// NOW-list gate applies. Every dimension below fails closed when unconfigured — a boxed
// Boolean field on SubscriptionVocabulary that is null (dimension not sent on this
// subscription, confirmed real per the fixture) is treated identically to an explicit false.
// Only applySubscriptionRules == false (or no subscriptionVocabulary at all) genuinely
// defaults to pass.
@Component
public class CPNowSubscriptionMatcher {

    public boolean matches(final CPNowSubscription subscription, final CPVocabulary vocabulary,
                            final List<JudicialResult> eligibleResults) {
        final SubscriptionVocabulary subVoc = subscription.getSubscriptionVocabulary();
        return !subscription.isApplySubscriptionRules()
                || subVoc == null
                || matchesVocabularyRules(subVoc, vocabulary, eligibleResults);
    }

    private boolean matchesVocabularyRules(final SubscriptionVocabulary subVoc, final CPVocabulary vocabulary,
                                            final List<JudicialResult> eligibleResults) {
        return (isTrue(subVoc.getIsCpsProsecuted()) && vocabulary.cpsProsecuted())
                || (attendanceMatches(subVoc)
                        && majorCreditorTypeMatches(subVoc)
                        && courtLanguageMatches(subVoc, vocabulary)
                        && ageGroupMatches(subVoc, vocabulary)
                        && custodyMatches(subVoc, vocabulary)
                        && custodialOutcomeMatches(subVoc, vocabulary)
                        && promptListsMatch(subVoc, eligibleResults)
                        && resultTypeListsMatch(subVoc, eligibleResults));
    }

    // Real appearedInPerson/appearedByVideoLink source (hearing.defendantAttendance) is
    // unconfirmed on our own HearingDetailsResponse (design doc §7) — CPVocabulary carries no
    // attendance facts yet. anyAppearance still bypasses per real behaviour; a specific
    // requirement without it can never be satisfied until that fixture gap is closed.
    private boolean attendanceMatches(final SubscriptionVocabulary subVoc) {
        return isTrue(subVoc.getAnyAppearance());
    }

    private boolean majorCreditorTypeMatches(final SubscriptionVocabulary subVoc) {
        // CPVocabulary.prosecutorMajorCreditor()/nonProsecutorMajorCreditor() are always empty,
        // non-null lists for PCR (design doc §2) — a specific requirement without
        // anyMajorCreditor can never be satisfied.
        return isTrue(subVoc.getAnyMajorCreditor())
                || (!isTrue(subVoc.getRequiresProsecutorMajorCreditor())
                        && !isTrue(subVoc.getRequiresNonProsecutorMajorCreditor()));
    }

    private boolean courtLanguageMatches(final SubscriptionVocabulary subVoc, final CPVocabulary vocabulary) {
        return isTrue(subVoc.getAnyCourtHearing())
                || (isTrue(subVoc.getEnglishCourtHearing()) && vocabulary.englishCourtHearing())
                || (isTrue(subVoc.getWelshCourtHearing()) && vocabulary.welshCourtHearing());
    }

    private boolean ageGroupMatches(final SubscriptionVocabulary subVoc, final CPVocabulary vocabulary) {
        return isTrue(subVoc.getAdultOrYouthDefendant())
                || (isTrue(subVoc.getYouthDefendant()) && vocabulary.youthDefendant())
                || (isTrue(subVoc.getAdultDefendant()) && vocabulary.adultDefendant());
    }

    private boolean custodyMatches(final SubscriptionVocabulary subVoc, final CPVocabulary vocabulary) {
        final boolean custodyLocationIsPolice = isTrue(subVoc.getCustodyLocationIsPolice());
        final boolean custodyLocationIsPrison = isTrue(subVoc.getCustodyLocationIsPrison());
        final boolean inCustody = isTrue(subVoc.getInCustody());
        return isTrue(subVoc.getIgnoreCustody())
                || (inCustody && !custodyLocationIsPolice && !custodyLocationIsPrison && vocabulary.inCustody())
                || (inCustody && custodyLocationIsPolice && !custodyLocationIsPrison
                        && vocabulary.custodyLocationIsPolice())
                || (inCustody && !custodyLocationIsPolice && custodyLocationIsPrison
                        && vocabulary.custodyLocationIsPrison());
    }

    private boolean custodialOutcomeMatches(final SubscriptionVocabulary subVoc, final CPVocabulary vocabulary) {
        final boolean custodialOutcomeMatches =
                isTrue(subVoc.getAtleastOneCustodialResult()) == vocabulary.atleastOneCustodialResult();
        return isTrue(subVoc.getIgnoreResults())
                || (isTrue(subVoc.getAllNonCustodialResults()) && vocabulary.allNonCustodialResults()
                        && custodialOutcomeMatches)
                || (isTrue(subVoc.getAtleastOneNonCustodialResult()) && vocabulary.atleastOneNonCustodialResult()
                        && custodialOutcomeMatches);
    }

    private boolean promptListsMatch(final SubscriptionVocabulary subVoc, final List<JudicialResult> results) {
        final List<String> promptReferences = results.stream()
                .filter(r -> r.getJudicialResultPrompts() != null)
                .flatMap(r -> r.getJudicialResultPrompts().stream())
                .map(JudicialResultPrompt::getPromptReference)
                .toList();
        return listMatches(subVoc.getIncludedPrompts(), promptReferences, true)
                && listMatches(subVoc.getExcludedPrompts(), promptReferences, false);
    }

    private boolean resultTypeListsMatch(final SubscriptionVocabulary subVoc, final List<JudicialResult> results) {
        final List<String> resultCodes = results.stream().map(JudicialResult::getCjsCode).toList();
        return listMatches(subVoc.getIncludedResults(), resultCodes, true)
                && listMatches(subVoc.getExcludedResults(), resultCodes, false);
    }

    private boolean listMatches(final List<String> configured, final List<String> actual, final boolean isInclude) {
        return configured == null
                || configured.isEmpty()
                || isInclude == actual.stream().anyMatch(value -> containsIgnoreCase(configured, value));
    }

    private boolean containsIgnoreCase(final List<String> values, final String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private boolean isTrue(final Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}