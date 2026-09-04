package uk.gov.hmcts.cp.services.pcrcompute;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.clients.ReferenceDataClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.pcrcompute.CPNowSubscription;
import uk.gov.hmcts.cp.domain.pcrcompute.CPVocabulary;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CPResultsPcrFilter {

    private final CPNowSubscriptionMatcher nowSubscriptionMatcher;
    private final ReferenceDataClient referenceDataClient;

    public List<JudicialResult> excludePublishedForNows(final List<JudicialResult> results) {
        // Plain field filter, no lookup.
        return results.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPublishedForNows()))
                .toList();
    }

    // Only subscriptions flagged isPrisonCourtRegisterSubscription are considered. Fetched once
    // per hearing (activeAt is hearing-wide) and passed into isPrisonCourtRegisterRequired for every defendant.
    public List<CPNowSubscription> fetchPrisonCourtRegisterSubscriptions(final LocalDate activeAt) {
        return referenceDataClient.getPrisonCourtRegisterSubscriptions(activeAt).stream()
                .filter(CPNowSubscription::isPrisonCourtRegisterSubscription)
                .toList();
    }

    // The generation gate.
    public boolean isPrisonCourtRegisterRequired(final CPVocabulary vocabulary, final List<JudicialResult> eligibleResults,
                                                  final List<CPNowSubscription> subscriptions) {
        return subscriptions.stream().anyMatch(s -> nowSubscriptionMatcher.matches(s, vocabulary, eligibleResults));
    }
}