package uk.gov.hmcts.cp.services;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.NowSubscription;
import uk.gov.hmcts.cp.domain.NowSubscription.SubscriptionVocabulary;
import uk.gov.hmcts.cp.domain.Vocabulary;

import java.util.List;

// Matches design doc §4 / SubscriptionsService.js's matchVocabularyRules — PCR subscriptions
// (isPrisonCourtRegisterSubscription) are matched by this alone, no court-house/prosecutor/
// NOW-list gate applies. Every dimension below fails closed when unconfigured; only
// applySubscriptionRules == false (or no subscriptionVocabulary at all) genuinely defaults to
// pass.
@Component
public class NowSubscriptionMatcher {

    public boolean matches(final NowSubscription subscription, final Vocabulary vocabulary,
                            final List<JudicialResult> eligibleResults) {
        final SubscriptionVocabulary subVoc = subscription.getSubscriptionVocabulary();
        return !subscription.isApplySubscriptionRules()
                || subVoc == null
                || matchesVocabularyRules(subVoc, vocabulary, eligibleResults);
    }

    private boolean matchesVocabularyRules(final SubscriptionVocabulary subVoc, final Vocabulary vocabulary,
                                            final List<JudicialResult> eligibleResults) {
        return (subVoc.isCpsProsecuted() && vocabulary.cpsProsecuted())
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
    // unconfirmed on our own HearingDetailsResponse (design doc §7) — Vocabulary carries no
    // attendance facts yet. anyAppearance still bypasses per real behaviour; a specific
    // requirement without it can never be satisfied until that fixture gap is closed.
    private boolean attendanceMatches(final SubscriptionVocabulary subVoc) {
        return subVoc.isAnyAppearance();
    }

    private boolean majorCreditorTypeMatches(final SubscriptionVocabulary subVoc) {
        // Vocabulary.prosecutorMajorCreditor()/nonProsecutorMajorCreditor() are always empty,
        // non-null lists for PCR (design doc §2) — a specific requirement without
        // anyMajorCreditor can never be satisfied.
        return subVoc.isAnyMajorCreditor()
                || (!subVoc.isRequiresProsecutorMajorCreditor() && !subVoc.isRequiresNonProsecutorMajorCreditor());
    }

    private boolean courtLanguageMatches(final SubscriptionVocabulary subVoc, final Vocabulary vocabulary) {
        return subVoc.isAnyCourtHearing()
                || (subVoc.isEnglishCourtHearing() && vocabulary.englishCourtHearing())
                || (subVoc.isWelshCourtHearing() && vocabulary.welshCourtHearing());
    }

    private boolean ageGroupMatches(final SubscriptionVocabulary subVoc, final Vocabulary vocabulary) {
        return subVoc.isAdultOrYouthDefendant()
                || (subVoc.isYouthDefendant() && vocabulary.youthDefendant())
                || (subVoc.isAdultDefendant() && vocabulary.adultDefendant());
    }

    private boolean custodyMatches(final SubscriptionVocabulary subVoc, final Vocabulary vocabulary) {
        return subVoc.isIgnoreCustody()
                || (subVoc.isInCustody() && !subVoc.isCustodyLocationIsPolice() && !subVoc.isCustodyLocationIsPrison()
                        && vocabulary.inCustody())
                || (subVoc.isInCustody() && subVoc.isCustodyLocationIsPolice() && !subVoc.isCustodyLocationIsPrison()
                        && vocabulary.custodyLocationIsPolice())
                || (subVoc.isInCustody() && !subVoc.isCustodyLocationIsPolice() && subVoc.isCustodyLocationIsPrison()
                        && vocabulary.custodyLocationIsPrison());
    }

    private boolean custodialOutcomeMatches(final SubscriptionVocabulary subVoc, final Vocabulary vocabulary) {
        final boolean custodialOutcomeMatches =
                subVoc.isAtleastOneCustodialResult() == vocabulary.atleastOneCustodialResult();
        return subVoc.isIgnoreResults()
                || (subVoc.isAllNonCustodialResults() && vocabulary.allNonCustodialResults() && custodialOutcomeMatches)
                || (subVoc.isAtleastOneNonCustodialResult() && vocabulary.atleastOneNonCustodialResult()
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
}