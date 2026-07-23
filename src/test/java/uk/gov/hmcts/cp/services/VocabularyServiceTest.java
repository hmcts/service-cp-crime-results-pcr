package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Prosecutor;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Respondent;
import uk.gov.hmcts.cp.domain.Vocabulary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyServiceTest {

    private static final String DEFENDANT_ID = "00000000-0000-0000-0000-000000000022";
    private static final String OTHER_DEFENDANT_ID = "00000000-0000-0000-0000-000000000033";
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String OTHER_MASTER_DEFENDANT_ID = "44444444-4444-4444-4444-444444444444";
    private static final String CUSTODIAL_RESULT_PROMPT = "prisonOrganisationName";

    private final VocabularyService vocabularyService = new VocabularyService();

    @Test
    void compute_should_setCustodyLocationIsPrison_whenOwnEstablishmentIsPrison() {
        final Defendant defendant = defendantWithEstablishment(DEFENDANT_ID, MASTER_DEFENDANT_ID, "Prison");
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.custodyLocationIsPrison()).isTrue();
        assertThat(vocabulary.custodyLocationIsPolice()).isFalse();
    }

    @Test
    void compute_should_setCustodyLocationIsPolice_whenOwnEstablishmentIsPoliceStation() {
        final Defendant defendant = defendantWithEstablishment(DEFENDANT_ID, MASTER_DEFENDANT_ID, "Police Station");
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.custodyLocationIsPolice()).isTrue();
        assertThat(vocabulary.custodyLocationIsPrison()).isFalse();
    }

    @Test
    void compute_should_setInCustodyTrue_whenEitherCustodyLocationTrue() {
        final Defendant defendant = defendantWithEstablishment(DEFENDANT_ID, MASTER_DEFENDANT_ID, "Prison");
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.inCustody()).isTrue();
    }

    @Test
    void compute_should_setInCustodyFalse_whenNoEstablishmentAnywhere() {
        final Defendant defendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.inCustody()).isFalse();
    }

    @Test
    void compute_should_mergeCustodyAcrossProsecutionCases_whenSameMasterDefendantIdOnSameHearing() {
        // Same physical person (masterDefendantId), two separate case URNs on the same hearing —
        // a valid CP scenario. Custody is only recorded against the OTHER case's defendant record.
        final Defendant ownCaseDefendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final Defendant otherCaseDefendant = defendantWithEstablishment(OTHER_DEFENDANT_ID, MASTER_DEFENDANT_ID, "Prison");
        final HearingDetail hearing = hearingWith(List.of(caseWith(ownCaseDefendant), caseWith(otherCaseDefendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(ownCaseDefendant, hearing);

        assertThat(vocabulary.custodyLocationIsPrison()).isTrue();
    }

    @Test
    void compute_should_notMergeCustody_whenOtherCaseDefendantHasDifferentMasterDefendantId() {
        final Defendant ownCaseDefendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final Defendant unrelatedDefendant = defendantWithEstablishment(OTHER_DEFENDANT_ID, OTHER_MASTER_DEFENDANT_ID, "Prison");
        final HearingDetail hearing = hearingWith(List.of(caseWith(ownCaseDefendant), caseWith(unrelatedDefendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(ownCaseDefendant, hearing);

        assertThat(vocabulary.custodyLocationIsPrison()).isFalse();
    }

    @Test
    void compute_should_mergeCustodyFromCourtApplication_whenRespondentSharesMasterDefendantId() {
        final Defendant defendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .respondents(List.of(Respondent.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()))
                .courtApplicationCases(List.of())
                .judicialResults(List.of(resultWithCustodialPrompt()))
                .build();
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of(application));

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.atleastOneCustodialResult()).isTrue();
    }

    @Test
    void compute_should_setAtleastOneCustodialResultTrue_whenAnyResultHasCustodialPrompt() {
        final Offence offence = Offence.builder().judicialResults(List.of(resultWithCustodialPrompt())).build();
        final Defendant defendant = defendantWithOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID, List.of(offence));
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.atleastOneCustodialResult()).isTrue();
        assertThat(vocabulary.allNonCustodialResults()).isFalse();
    }

    @Test
    void compute_should_setAllNonCustodialResultsTrue_whenNoResultHasCustodialPrompt() {
        final Offence offence = Offence.builder().judicialResults(List.of(resultWithoutCustodialPrompt())).build();
        final Defendant defendant = defendantWithOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID, List.of(offence));
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.allNonCustodialResults()).isTrue();
        assertThat(vocabulary.atleastOneNonCustodialResult()).isTrue();
        assertThat(vocabulary.atleastOneCustodialResult()).isFalse();
    }

    @Test
    void compute_should_setCpsProsecutedTrue_whenAnyProsecutionCaseOnHearingIsCps() {
        // Scans ALL prosecutionCases on the hearing, not scoped to the defendant's own case —
        // replicates legacy VocabularyService.js behaviour exactly (design doc §2).
        final Defendant ownCaseDefendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final Defendant otherCaseDefendant = defendantWithNoOffences(OTHER_DEFENDANT_ID, OTHER_MASTER_DEFENDANT_ID);
        final ProsecutionCase ownCase = caseWith(ownCaseDefendant);
        final ProsecutionCase cpsCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN("CPSCASE001").build())
                .caseMarkers(List.of())
                .defendants(List.of(otherCaseDefendant))
                .prosecutor(Prosecutor.builder().isCps(true).build())
                .build();
        final HearingDetail hearing = hearingWith(List.of(ownCase, cpsCase), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(ownCaseDefendant, hearing);

        assertThat(vocabulary.cpsProsecuted()).isTrue();
    }

    @Test
    void compute_should_setCpsProsecutedFalse_whenNoProsecutionCaseOnHearingIsCps() {
        final Defendant defendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.cpsProsecuted()).isFalse();
    }

    @Test
    void compute_should_setYouthDefendantTrue_whenDefendantIsYouth() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID)
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .isYouth(true)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.youthDefendant()).isTrue();
        assertThat(vocabulary.adultDefendant()).isFalse();
    }

    @Test
    void compute_should_setWelshCourtHearingTrue_whenCourtCentreIsWelsh() {
        final Defendant defendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().welshCourtCentre(true).build())
                .hearingDays(List.of())
                .prosecutionCases(List.of(caseWith(defendant)))
                .courtApplications(List.of())
                .build();

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.welshCourtHearing()).isTrue();
        assertThat(vocabulary.englishCourtHearing()).isFalse();
    }

    @Test
    void compute_should_returnEmptyMajorCreditorLists_always() {
        final Defendant defendant = defendantWithNoOffences(DEFENDANT_ID, MASTER_DEFENDANT_ID);
        final HearingDetail hearing = hearingWith(List.of(caseWith(defendant)), List.of());

        final Vocabulary vocabulary = vocabularyService.compute(defendant, hearing);

        assertThat(vocabulary.prosecutorMajorCreditor()).isEmpty();
        assertThat(vocabulary.nonProsecutorMajorCreditor()).isEmpty();
    }

    private Defendant defendantWithEstablishment(final String id, final String masterDefendantId, final String custody) {
        return Defendant.builder()
                .id(id)
                .masterDefendantId(masterDefendantId)
                .personDefendant(PersonDefendant.builder()
                        .custodialEstablishment(CustodialEstablishment.builder().custody(custody).build())
                        .build())
                .offences(List.of())
                .build();
    }

    private Defendant defendantWithNoOffences(final String id, final String masterDefendantId) {
        return Defendant.builder()
                .id(id)
                .masterDefendantId(masterDefendantId)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
    }

    private Defendant defendantWithOffences(final String id, final String masterDefendantId, final List<Offence> offences) {
        return Defendant.builder()
                .id(id)
                .masterDefendantId(masterDefendantId)
                .personDefendant(PersonDefendant.builder().build())
                .offences(offences)
                .build();
    }

    private ProsecutionCase caseWith(final Defendant defendant) {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN("ABCD1234567").build())
                .caseMarkers(List.of())
                .defendants(List.of(defendant))
                .build();
    }

    private HearingDetail hearingWith(final List<ProsecutionCase> cases, final List<CourtApplication> applications) {
        return HearingDetail.builder()
                .courtCentre(CourtCentre.builder().build())
                .hearingDays(List.of())
                .prosecutionCases(cases)
                .courtApplications(applications)
                .build();
    }

    private JudicialResult resultWithCustodialPrompt() {
        return JudicialResult.builder()
                .judicialResultPrompts(List.of(JudicialResultPrompt.builder().promptReference(CUSTODIAL_RESULT_PROMPT).build()))
                .build();
    }

    private JudicialResult resultWithoutCustodialPrompt() {
        return JudicialResult.builder()
                .judicialResultPrompts(List.of())
                .build();
    }
}