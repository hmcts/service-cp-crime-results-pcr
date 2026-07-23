package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudicialResultPromptParserTest {

    private final JudicialResultPromptParser parser = new JudicialResultPromptParser();

    @Test
    void concurrent_should_parseBoolean_whenPromptPresent() {
        final JudicialResult result = resultWithPrompt("concurrent", "true");

        assertThat(parser.concurrent(result)).isTrue();
    }

    @Test
    void concurrent_should_returnNull_whenPromptAbsent() {
        final JudicialResult result = resultWithPrompt("other", "value");

        assertThat(parser.concurrent(result)).isNull();
    }

    @Test
    void consecutiveToDate_should_parseDate_whenPromptPresent() {
        final JudicialResult result = resultWithPrompt("consecutiveToSentenceImposedOn", "2026-06-23");

        assertThat(parser.consecutiveToDate(result)).isEqualTo(LocalDate.of(2026, 6, 23));
    }

    @Test
    void consecutiveToCourtName_should_returnValue_whenPromptPresent() {
        final JudicialResult result = resultWithPrompt("whichWasImpBy", "Aberdeen Sheriff Court District");

        assertThat(parser.consecutiveToCourtName(result)).isEqualTo("Aberdeen Sheriff Court District");
    }

    @Test
    void fineAmount_should_stripCurrencySymbolAndParse() {
        final JudicialResult result = resultWithPrompt("AOF", "£6787.00");

        assertThat(parser.fineAmount(result)).isEqualTo(6787.00);
    }

    @Test
    void imprisonmentPeriod_should_returnRawValue() {
        final JudicialResult result = resultWithPrompt("imprisonmentPeriod", "6 Years");

        assertThat(parser.imprisonmentPeriod(result)).isEqualTo("6 Years");
    }

    @Test
    void totalCustodialPeriod_should_returnRawValue() {
        final JudicialResult result = resultWithPrompt("totalCustodialPeriod", "6 Months 1 week");

        assertThat(parser.totalCustodialPeriod(result)).isEqualTo("6 Months 1 week");
    }

    private JudicialResult resultWithPrompt(final String promptReference, final String value) {
        return JudicialResult.builder()
                .judicialResultPrompts(List.of(JudicialResultPrompt.builder()
                        .promptReference(promptReference)
                        .value(value)
                        .build()))
                .build();
    }
}