package uk.gov.hmcts.cp.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class JudicialResultPromptParser {

    private static final String CONCURRENT_PROMPT = "concurrent";
    private static final String CONSECUTIVE_TO_DATE_PROMPT = "consecutiveToSentenceImposedOn";
    private static final String CONSECUTIVE_TO_COURT_PROMPT = "whichWasImpBy";
    private static final String FINE_AMOUNT_PROMPT = "AOF";
    private static final String IMPRISONMENT_PERIOD_PROMPT = "imprisonmentPeriod";
    private static final String TOTAL_CUSTODIAL_PERIOD_PROMPT = "totalCustodialPeriod";

    public Boolean concurrent(final JudicialResult result) {
        return findPrompt(result, CONCURRENT_PROMPT).map(Boolean::parseBoolean).orElse(null);
    }

    public LocalDate consecutiveToDate(final JudicialResult result) {
        return findPrompt(result, CONSECUTIVE_TO_DATE_PROMPT).map(LocalDate::parse).orElse(null);
    }

    public String consecutiveToCourtName(final JudicialResult result) {
        return findPrompt(result, CONSECUTIVE_TO_COURT_PROMPT).orElse(null);
    }

    public Double fineAmount(final JudicialResult result) {
        return findPrompt(result, FINE_AMOUNT_PROMPT)
                .map(v -> v.replaceAll("[^0-9.]", ""))
                .map(Double::parseDouble)
                .orElse(null);
    }

    public String imprisonmentPeriod(final JudicialResult result) {
        return findPrompt(result, IMPRISONMENT_PERIOD_PROMPT).orElse(null);
    }

    public String totalCustodialPeriod(final JudicialResult result) {
        return findPrompt(result, TOTAL_CUSTODIAL_PERIOD_PROMPT).orElse(null);
    }

    private Optional<String> findPrompt(final JudicialResult result, final String promptReference) {
        return result.getJudicialResultPrompts().stream()
                .filter(p -> promptReference.equals(p.getPromptReference()))
                .map(JudicialResultPrompt::getValue)
                .findFirst();
    }
}