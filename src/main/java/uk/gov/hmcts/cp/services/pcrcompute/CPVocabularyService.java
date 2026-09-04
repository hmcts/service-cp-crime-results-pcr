package uk.gov.hmcts.cp.services.pcrcompute;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.pcrcompute.CPVocabulary;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class CPVocabularyService {

    private static final String CUSTODIAL_RESULT_PROMPT = "prisonOrganisationName";
    private static final String POLICE_STATION = "Police Station";
    private static final String PRISON = "Prison";

    public CPVocabulary compute(final Defendant defendant, final HearingDetail hearing) {
        final List<Defendant> masterDefendants = matchingDefendants(defendant, hearing);
        final List<JudicialResult> allResults = allJudicialResults(masterDefendants, matchingApplications(defendant, hearing));
        final boolean atleastOneCustodialResult = atleastOneCustodialResult(allResults);
        final boolean atleastOneNonCustodialResult = atleastOneCustodialResult
                ? hasNonCustodialPrompt(allResults)
                : true;
        final boolean custodyLocationIsPolice = hasCustodyValue(masterDefendants, POLICE_STATION);
        final boolean custodyLocationIsPrison = hasCustodyValue(masterDefendants, PRISON);
        final boolean isYouth = Boolean.TRUE.equals(defendant.getIsYouth());
        final boolean isWelshCourtHearing = Boolean.TRUE.equals(hearing.getCourtCentre().getWelshCourtCentre());

        return CPVocabulary.builder()
                .custodyLocationIsPolice(custodyLocationIsPolice)
                .custodyLocationIsPrison(custodyLocationIsPrison)
                .inCustody(custodyLocationIsPolice || custodyLocationIsPrison)
                .atleastOneCustodialResult(atleastOneCustodialResult)
                .allNonCustodialResults(!atleastOneCustodialResult)
                .atleastOneNonCustodialResult(atleastOneNonCustodialResult)
                .cpsProsecuted(cpsProsecuted(hearing))
                .youthDefendant(isYouth)
                .adultDefendant(!isYouth)
                .welshCourtHearing(isWelshCourtHearing)
                .englishCourtHearing(!isWelshCourtHearing)
                .prosecutorMajorCreditor(List.of())
                .nonProsecutorMajorCreditor(List.of())
                .build();
    }

    // Same masterDefendantId can appear as a separate Defendant record on more than one
    // prosecutionCase, and as a respondent on a court application, same hearing — a real scenario.
    // Every scan below merges across all of them. Always includes `defendant` itself — otherwise
    // an application-only defendant's own custody establishment gets silently dropped.
    private List<Defendant> matchingDefendants(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        return masterDefendantId == null
                ? List.of(defendant)
                : Stream.concat(Stream.of(defendant),
                        Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
                                .flatMap(c -> c.getDefendants().stream())
                                .filter(d -> masterDefendantId.equals(d.getMasterDefendantId())))
                        .toList();
    }

    // `subject` is the only party role used for defendant-linkage.
    private List<CourtApplication> matchingApplications(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        // courtApplications can be absent entirely, not just an empty list.
        return masterDefendantId == null
                ? List.of()
                : Stream.ofNullable(hearing.getCourtApplications()).flatMap(List::stream)
                        .filter(a -> masterDefendantId.equals(subjectMasterDefendantId(a)))
                        .toList();
    }

    private String subjectMasterDefendantId(final CourtApplication application) {
        return application.getSubject() == null || application.getSubject().getMasterDefendant() == null
                ? null
                : application.getSubject().getMasterDefendant().getMasterDefendantId();
    }

    private List<JudicialResult> allJudicialResults(final List<Defendant> defendants, final List<CourtApplication> applications) {
        final Stream<JudicialResult> caseResults = defendants.stream()
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream());
        final Stream<JudicialResult> applicationResults = applications.stream()
                .flatMap(a -> a.getJudicialResults().stream());
        // courtApplicationCase can omit "offences" entirely, not just an empty list.
        final Stream<JudicialResult> linkedOffenceResults = applications.stream()
                .flatMap(a -> a.getCourtApplicationCases().stream())
                .flatMap(c -> Stream.ofNullable(c.getOffences()).flatMap(List::stream))
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

    // Scans every prompt on every result for any promptReference other than the custodial one —
    // not "a result with no custodial prompt" (that's allNonCustodialResults).
    private boolean hasNonCustodialPrompt(final List<JudicialResult> results) {
        return results.stream()
                .flatMap(r -> Stream.ofNullable(r.getJudicialResultPrompts()).flatMap(List::stream))
                .map(JudicialResultPrompt::getPromptReference)
                .anyMatch(ref -> ref != null && !CUSTODIAL_RESULT_PROMPT.equals(ref));
    }

    private boolean hasCustodialPrompt(final JudicialResult result) {
        // judicialResultPrompts can be absent entirely, not just an empty list.
        return Stream.ofNullable(result.getJudicialResultPrompts()).flatMap(List::stream)
                .map(JudicialResultPrompt::getPromptReference)
                .anyMatch(CUSTODIAL_RESULT_PROMPT::equals);
    }

    private boolean cpsProsecuted(final HearingDetail hearing) {
        // Scans all prosecutionCases on the hearing for prosecutor.isCps == true — not scoped to the defendant's own case.
        return Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
                .map(ProsecutionCase::getProsecutor)
                .filter(Objects::nonNull)
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsCps()));
    }
}