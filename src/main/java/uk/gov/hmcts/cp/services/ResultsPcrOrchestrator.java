package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.clients.ReferenceDataClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.NowSubscription;
import uk.gov.hmcts.cp.domain.Vocabulary;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ResultsPcrOrchestrator {

    private final NowSubscriptionMatcher nowSubscriptionMatcher;
    private final ReferenceDataClient referenceDataClient;

    public List<JudicialResult> excludePublishedForNows(final List<JudicialResult> results) {
        // Mirrors RegisterFragmentService.filterJudicialResultsApplicableForRegisters
        // (design doc §3) — a plain field filter, no lookup.
        return results.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPublishedForNows()))
                .toList();
    }

    // Design doc §4 — the generation gate. Only subscriptions actually flagged
    // isPrisonCourtRegisterSubscription are considered (SubscriptionsService.js dispatcher
    // branch 4) — a subscription of any other kind is never PCR-eligible regardless of
    // whether its vocabulary would otherwise match.
    public boolean isPrisonCourtRegisterRequired(final Vocabulary vocabulary,
                                                  final List<JudicialResult> eligibleResults, final LocalDate on) {
        return referenceDataClient.getPrisonCourtRegisterSubscriptions(on).stream()
                .filter(NowSubscription::isPrisonCourtRegisterSubscription)
                .anyMatch(s -> nowSubscriptionMatcher.matches(s, vocabulary, eligibleResults));
    }
}