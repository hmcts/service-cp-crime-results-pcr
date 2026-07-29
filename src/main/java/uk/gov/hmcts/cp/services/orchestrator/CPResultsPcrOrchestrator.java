package uk.gov.hmcts.cp.services.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.clients.orchestrator.ReferenceDataClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CPResultsPcrOrchestrator {

    private final CPNowSubscriptionMatcher nowSubscriptionMatcher;
    private final ReferenceDataClient referenceDataClient;

    public List<JudicialResult> excludePublishedForNows(final List<JudicialResult> results) {
        // Mirrors RegisterFragmentService.filterJudicialResultsApplicableForRegisters
        // (design doc §3) — a plain field filter, no lookup.
        return results.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPublishedForNows()))
                .toList();
    }

    // Design doc §4 — only subscriptions actually flagged isPrisonCourtRegisterSubscription
    // are considered (SubscriptionsService.js dispatcher branch 4) — a subscription of any
    // other kind is never PCR-eligible regardless of whether its vocabulary would otherwise
    // match. Fetched once per hearing by the caller (activeAt is hearing-wide, not
    // per-defendant) and passed into isPrisonCourtRegisterRequired for every defendant.
    public List<CPNowSubscription> fetchPrisonCourtRegisterSubscriptions(final LocalDate activeAt) {
        return referenceDataClient.getPrisonCourtRegisterSubscriptions(activeAt).stream()
                .filter(CPNowSubscription::isPrisonCourtRegisterSubscription)
                .toList();
    }

    // Design doc §4 — the generation gate.
    public boolean isPrisonCourtRegisterRequired(final CPVocabulary vocabulary, final List<JudicialResult> eligibleResults,
                                                  final List<CPNowSubscription> subscriptions) {
        return subscriptions.stream().anyMatch(s -> nowSubscriptionMatcher.matches(s, vocabulary, eligibleResults));
    }
}