package uk.gov.hmcts.cp.services.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.clients.orchestrator.ReferenceDataClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.orchestrator.NowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.Vocabulary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    public boolean isPrisonCourtRegisterRequired(final Vocabulary vocabulary, final List<JudicialResult> eligibleResults) {
        return orderedDate(eligibleResults)
                .map(on -> referenceDataClient.getPrisonCourtRegisterSubscriptions(on).stream()
                        .filter(NowSubscription::isPrisonCourtRegisterSubscription)
                        .anyMatch(s -> nowSubscriptionMatcher.matches(s, vocabulary, eligibleResults)))
                .orElse(false);
    }

    // Faithful port of PrisonCourtRegisterSubscriptions/index.js:52-57's getOrderedDate
    // (design doc §7) — confirmed real via cpp-context-results's results-query-api RAML schema.
    // Legacy finds the first result with a non-null orderedDate to decide a date exists at all,
    // then returns the FIRST result's orderedDate regardless of whether that specific result is
    // the one that matched — not sorted, not the latest. Preserved as-is, including the case
    // where results[0] itself lacks a date even though a later result has one — that's a real
    // quirk, not something this service "fixes". No result with any orderedDate at all means no
    // date exists to select a subscription batch against, so no Reference Data call is made — a
    // deliberate, safer deviation from legacy's undefined-object crash in that case.
    private Optional<LocalDate> orderedDate(final List<JudicialResult> results) {
        return results.stream().noneMatch(r -> r.getOrderedDate() != null)
                ? Optional.empty()
                : Optional.ofNullable(results.get(0).getOrderedDate());
    }
}
