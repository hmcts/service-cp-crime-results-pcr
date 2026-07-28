package uk.gov.hmcts.cp.services.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.clients.orchestrator.ReferenceDataClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.orchestrator.NowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.Vocabulary;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsPcrOrchestratorTest {

    private static final LocalDate ORDERED_DATE = LocalDate.of(2026, 7, 23);

    @Mock
    private NowSubscriptionMatcher nowSubscriptionMatcher;
    @Mock
    private ReferenceDataClient referenceDataClient;

    @InjectMocks
    private ResultsPcrOrchestrator resultsPcrOrchestrator;

    @Test
    void excludePublishedForNows_should_removeResultsMarkedPublishedForNows() {
        final JudicialResult published = JudicialResult.builder().cjsCode("1200").publishedForNows(true).build();
        final JudicialResult eligible = JudicialResult.builder().cjsCode("1300").publishedForNows(false).build();

        final List<JudicialResult> result = resultsPcrOrchestrator.excludePublishedForNows(List.of(published, eligible));

        assertThat(result).containsExactly(eligible);
    }

    @Test
    void excludePublishedForNows_should_returnEmptyList_whenAllResultsPublishedForNows() {
        final JudicialResult published = JudicialResult.builder().cjsCode("1200").publishedForNows(true).build();

        final List<JudicialResult> result = resultsPcrOrchestrator.excludePublishedForNows(List.of(published));

        assertThat(result).isEmpty();
    }

    @Test
    void isPrisonCourtRegisterRequired_should_returnTrue_whenPcrSubscriptionMatches() {
        final Vocabulary vocabulary = vocabulary();
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").orderedDate(ORDERED_DATE).build();
        final List<JudicialResult> eligibleResults = List.of(result);
        final NowSubscription pcrSubscription = NowSubscription.builder()
                .isPrisonCourtRegisterSubscription(true)
                .build();
        when(referenceDataClient.getPrisonCourtRegisterSubscriptions(ORDERED_DATE)).thenReturn(List.of(pcrSubscription));
        when(nowSubscriptionMatcher.matches(pcrSubscription, vocabulary, eligibleResults)).thenReturn(true);

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary, eligibleResults)).isTrue();
    }

    @Test
    void isPrisonCourtRegisterRequired_should_returnFalse_whenNoSubscriptionMatches() {
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").orderedDate(ORDERED_DATE).build();
        final List<JudicialResult> eligibleResults = List.of(result);
        final NowSubscription pcrSubscription = NowSubscription.builder()
                .isPrisonCourtRegisterSubscription(true)
                .build();
        when(referenceDataClient.getPrisonCourtRegisterSubscriptions(ORDERED_DATE)).thenReturn(List.of(pcrSubscription));
        when(nowSubscriptionMatcher.matches(pcrSubscription, vocabulary(), eligibleResults)).thenReturn(false);

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary(), eligibleResults)).isFalse();
    }

    @Test
    void isPrisonCourtRegisterRequired_should_ignoreNonPcrSubscriptions() {
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").orderedDate(ORDERED_DATE).build();
        final List<JudicialResult> eligibleResults = List.of(result);
        final NowSubscription nonPcrSubscription = NowSubscription.builder()
                .isPrisonCourtRegisterSubscription(false)
                .build();
        when(referenceDataClient.getPrisonCourtRegisterSubscriptions(ORDERED_DATE)).thenReturn(List.of(nonPcrSubscription));

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary(), eligibleResults)).isFalse();
        verify(nowSubscriptionMatcher, never()).matches(any(), any(), any());
    }

    @Test
    void isPrisonCourtRegisterRequired_should_returnFalse_whenNoResultHasOrderedDate() {
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").build();
        final List<JudicialResult> eligibleResults = List.of(result);

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary(), eligibleResults)).isFalse();
        verify(referenceDataClient, never()).getPrisonCourtRegisterSubscriptions(any());
    }

    @Test
    void isPrisonCourtRegisterRequired_should_returnFalse_whenFirstResultLacksOrderedDateEvenIfALaterOneHasIt() {
        // Faithful port of PrisonCourtRegisterSubscriptions/index.js:52-57's getOrderedDate quirk
        // (orchestrator design doc §7): legacy finds the first fragment with ANY dated result,
        // then returns that fragment's FIRST result's date regardless of whether that specific
        // result is the one that matched. Translated to our single-defendant scope: if results[0]
        // has no date, the derived date is null even though a later result in the same list does
        // — not "fixed" to search further, faithfully replicated as a real, documented quirk.
        final JudicialResult first = JudicialResult.builder().cjsCode("1200").build();
        final JudicialResult second = JudicialResult.builder().cjsCode("1300").orderedDate(ORDERED_DATE).build();
        final List<JudicialResult> eligibleResults = List.of(first, second);

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary(), eligibleResults)).isFalse();
        verify(referenceDataClient, never()).getPrisonCourtRegisterSubscriptions(any());
    }

    private static Vocabulary vocabulary() {
        return new Vocabulary(
                false, false, false,
                false, false, false,
                false,
                false, true,
                false, true,
                List.of(), List.of());
    }
}
