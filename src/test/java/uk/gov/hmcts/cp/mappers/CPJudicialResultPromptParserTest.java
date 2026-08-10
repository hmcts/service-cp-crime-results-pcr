package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CPJudicialResultPromptParserTest {

    private final CPJudicialResultPromptParser parser = new CPJudicialResultPromptParser();

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
        final JudicialResult result = resultWithPrompt("consecutiveToSentenceImposedOn", "23/06/2026");

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
    void totalCustodialPeriod_should_returnRawValue_whenTotalCustodialPeriodPromptPresent() {
        final JudicialResult result = resultWithPrompt("totalCustodialPeriod", "6 Months 1 week");

        assertThat(parser.totalCustodialPeriod(result)).isEqualTo("6 Months 1 week");
    }

    @Test
    void totalCustodialPeriod_should_fallBackToImprisonmentPeriod_whenTotalCustodialPeriodPromptAbsent() {
        final JudicialResult result = resultWithPrompt("imprisonmentPeriod", "8 Years");

        assertThat(parser.totalCustodialPeriod(result)).isEqualTo("8 Years");
    }

    @Test
    void totalCustodialPeriod_should_returnLife_whenIsLifePromptTrue() {
        final JudicialResult result = resultWithPrompts(
                JudicialResultPrompt.builder().promptReference("totalCustodialPeriodIsLife").value("true").build(),
                JudicialResultPrompt.builder().promptReference("imprisonmentPeriod").value("8 Years").build());

        assertThat(parser.totalCustodialPeriod(result)).isEqualTo("Life");
    }

    @Test
    void totalCustodialPeriod_should_returnNull_whenNoDurationOrLifePromptPresent() {
        final JudicialResult result = resultWithPrompt("other", "value");

        assertThat(parser.totalCustodialPeriod(result)).isNull();
    }

    private JudicialResult resultWithPrompt(final String promptReference, final String value) {
        return resultWithPrompts(JudicialResultPrompt.builder().promptReference(promptReference).value(value).build());
    }

    private JudicialResult resultWithPrompts(final JudicialResultPrompt... prompts) {
        return JudicialResult.builder().judicialResultPrompts(List.of(prompts)).build();
    }
}