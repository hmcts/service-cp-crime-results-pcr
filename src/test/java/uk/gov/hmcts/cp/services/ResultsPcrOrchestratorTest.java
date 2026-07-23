package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsPcrOrchestratorTest {

    private final ResultsPcrOrchestrator resultsPcrOrchestrator = new ResultsPcrOrchestrator();

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
}