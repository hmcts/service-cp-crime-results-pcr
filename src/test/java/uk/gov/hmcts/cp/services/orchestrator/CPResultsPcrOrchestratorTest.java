package uk.gov.hmcts.cp.services.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.clients.orchestrator.ReferenceDataClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CPResultsPcrOrchestratorTest {

    private static final LocalDate ON_DATE = LocalDate.of(2026, 7, 23);

    @Mock
    private CPNowSubscriptionMatcher nowSubscriptionMatcher;
    @Mock
    private ReferenceDataClient referenceDataClient;

    @InjectMocks
    private CPResultsPcrOrchestrator resultsPcrOrchestrator;

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
        final CPVocabulary vocabulary = vocabulary();
        final CPNowSubscription pcrSubscription = CPNowSubscription.builder()
                .isPrisonCourtRegisterSubscription(true)
                .build();
        when(referenceDataClient.getPrisonCourtRegisterSubscriptions(ON_DATE)).thenReturn(List.of(pcrSubscription));
        when(nowSubscriptionMatcher.matches(pcrSubscription, vocabulary, List.of())).thenReturn(true);

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary, List.of(), ON_DATE)).isTrue();
    }

    @Test
    void isPrisonCourtRegisterRequired_should_returnFalse_whenNoSubscriptionMatches() {
        final CPNowSubscription pcrSubscription = CPNowSubscription.builder()
                .isPrisonCourtRegisterSubscription(true)
                .build();
        when(referenceDataClient.getPrisonCourtRegisterSubscriptions(ON_DATE)).thenReturn(List.of(pcrSubscription));
        when(nowSubscriptionMatcher.matches(pcrSubscription, vocabulary(), List.of())).thenReturn(false);

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary(), List.of(), ON_DATE)).isFalse();
    }

    @Test
    void isPrisonCourtRegisterRequired_should_ignoreNonPcrSubscriptions() {
        final CPNowSubscription nonPcrSubscription = CPNowSubscription.builder()
                .isPrisonCourtRegisterSubscription(false)
                .build();
        when(referenceDataClient.getPrisonCourtRegisterSubscriptions(ON_DATE)).thenReturn(List.of(nonPcrSubscription));

        assertThat(resultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary(), List.of(), ON_DATE)).isFalse();
        verify(nowSubscriptionMatcher, never()).matches(any(), any(), any());
    }

    private static CPVocabulary vocabulary() {
        return new CPVocabulary(
                false, false, false,
                false, false, false,
                false,
                false, true,
                false, true,
                List.of(), List.of());
    }
}