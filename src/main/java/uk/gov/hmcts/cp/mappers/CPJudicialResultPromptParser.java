package uk.gov.hmcts.cp.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class CPJudicialResultPromptParser {

    private static final DateTimeFormatter CONSECUTIVE_TO_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String CONCURRENT_PROMPT = "concurrent";
    private static final String CONSECUTIVE_TO_DATE_PROMPT = "consecutiveToSentenceImposedOn";
    private static final String CONSECUTIVE_TO_COURT_PROMPT = "whichWasImpBy";
    private static final String FINE_AMOUNT_PROMPT = "AOF";
    private static final String IMPRISONMENT_PERIOD_PROMPT = "imprisonmentPeriod";
    private static final String TOTAL_CUSTODIAL_PERIOD_PROMPT = "totalCustodialPeriod";
    private static final String TOTAL_CUSTODIAL_PERIOD_IS_LIFE_PROMPT = "totalCustodialPeriodIsLife";
    private static final String LIFE = "Life";

    public Boolean concurrent(final JudicialResult result) {
        return findPrompt(result, CONCURRENT_PROMPT).map(Boolean::parseBoolean).orElse(null);
    }

    public LocalDate consecutiveToDate(final JudicialResult result) {
        return findPrompt(result, CONSECUTIVE_TO_DATE_PROMPT)
                .map(value -> LocalDate.parse(value, CONSECUTIVE_TO_DATE_FORMAT))
                .orElse(null);
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

    // "Life" takes priority over any duration prompt — a life sentence can still carry an
    // imprisonmentPeriod/totalCustodialPeriod value (e.g. a minimum term), but the overall
    // period is life regardless. Otherwise falls back from the specific totalCustodialPeriod
    // prompt to imprisonmentPeriod, since a single-offence result carries duration under
    // imprisonmentPeriod rather than a separate totalCustodialPeriod prompt.
    public String totalCustodialPeriod(final JudicialResult result) {
        if (isLife(result)) {
            return LIFE;
        }
        return findPrompt(result, TOTAL_CUSTODIAL_PERIOD_PROMPT)
                .or(() -> findPrompt(result, IMPRISONMENT_PERIOD_PROMPT))
                .orElse(null);
    }

    private boolean isLife(final JudicialResult result) {
        return findPrompt(result, TOTAL_CUSTODIAL_PERIOD_IS_LIFE_PROMPT)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private Optional<String> findPrompt(final JudicialResult result, final String promptReference) {
        // judicialResultPrompts absent entirely on a real judicial result that has none
        // (confirmed against a real hearing fixture) — not always an empty list.
        return Stream.ofNullable(result.getJudicialResultPrompts()).flatMap(List::stream)
                .filter(p -> promptReference.equals(p.getPromptReference()))
                .map(JudicialResultPrompt::getValue)
                .findFirst();
    }
}