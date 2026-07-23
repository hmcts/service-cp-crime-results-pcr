package uk.gov.hmcts.cp.services;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.Vocabulary;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class VocabularyService {

    private static final String CUSTODIAL_RESULT_PROMPT = "prisonOrganisationName";
    private static final String POLICE_STATION = "Police Station";
    private static final String PRISON = "Prison";

    public Vocabulary compute(final Defendant defendant, final HearingDetail hearing) {
        final List<Defendant> masterDefendants = matchingDefendants(defendant, hearing);
        final List<JudicialResult> allResults = allJudicialResults(masterDefendants, matchingApplications(defendant, hearing));
        final boolean atleastOneCustodialResult = atleastOneCustodialResult(allResults);
        final boolean custodyLocationIsPolice = hasCustodyValue(masterDefendants, POLICE_STATION);
        final boolean custodyLocationIsPrison = hasCustodyValue(masterDefendants, PRISON);
        final boolean isYouth = Boolean.TRUE.equals(defendant.getIsYouth());
        final boolean isWelshCourtHearing = Boolean.TRUE.equals(hearing.getCourtCentre().getWelshCourtCentre());

        return new Vocabulary(
                custodyLocationIsPolice,
                custodyLocationIsPrison,
                custodyLocationIsPolice || custodyLocationIsPrison,
                atleastOneCustodialResult,
                !atleastOneCustodialResult,
                atleastOneNonCustodialResult(allResults),
                cpsProsecuted(hearing),
                isYouth,
                !isYouth,
                isWelshCourtHearing,
                !isWelshCourtHearing,
                List.of(),
                List.of());
    }

    // Same physical person (masterDefendantId) can appear as a separate Defendant record on
    // more than one prosecutionCase, and as a respondent on a court application, all on the
    // same hearing — a valid CP scenario, not an edge case. Every hearing-wide scan below
    // merges across all of them, matching legacy VocabularyService.js (design doc §2).
    private List<Defendant> matchingDefendants(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        return masterDefendantId == null
                ? List.of(defendant)
                : hearing.getProsecutionCases().stream()
                        .flatMap(c -> c.getDefendants().stream())
                        .filter(d -> masterDefendantId.equals(d.getMasterDefendantId()))
                        .toList();
    }

    private List<CourtApplication> matchingApplications(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        return masterDefendantId == null
                ? List.of()
                : hearing.getCourtApplications().stream()
                        .filter(a -> a.getRespondents().stream()
                                .anyMatch(r -> masterDefendantId.equals(r.getMasterDefendantId())))
                        .toList();
    }

    private List<JudicialResult> allJudicialResults(final List<Defendant> defendants, final List<CourtApplication> applications) {
        final Stream<JudicialResult> caseResults = defendants.stream()
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream());
        final Stream<JudicialResult> applicationResults = applications.stream()
                .flatMap(a -> a.getJudicialResults().stream());
        final Stream<JudicialResult> linkedOffenceResults = applications.stream()
                .flatMap(a -> a.getCourtApplicationCases().stream())
                .flatMap(c -> c.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream());
        return Stream.of(caseResults, applicationResults, linkedOffenceResults).flatMap(s -> s).toList();
    }

    private boolean hasCustodyValue(final List<Defendant> defendants, final String custodyValue) {
        return defendants.stream()
                .map(Defendant::getPersonDefendant)
                .filter(Objects::nonNull)
                .map(PersonDefendant::getCustodialEstablishment)
                .filter(Objects::nonNull)
                .anyMatch(e -> custodyValue.equals(e.getCustody()));
    }

    private boolean atleastOneCustodialResult(final List<JudicialResult> results) {
        return results.stream().anyMatch(this::hasCustodialPrompt);
    }

    private boolean atleastOneNonCustodialResult(final List<JudicialResult> results) {
        return results.stream().anyMatch(r -> !hasCustodialPrompt(r));
    }

    private boolean hasCustodialPrompt(final JudicialResult result) {
        return result.getJudicialResultPrompts().stream()
                .map(JudicialResultPrompt::getPromptReference)
                .anyMatch(CUSTODIAL_RESULT_PROMPT::equals);
    }

    private boolean cpsProsecuted(final HearingDetail hearing) {
        // Scans ALL prosecutionCases on the hearing for prosecutor.isCps == true — not scoped
        // to the defendant's own case. Replicated as-is (design doc §2/§7).
        return hearing.getProsecutionCases().stream()
                .map(ProsecutionCase::getProsecutor)
                .filter(Objects::nonNull)
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsCps()));
    }
}