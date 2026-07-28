package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplicationCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Respondent;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CPVersionEntityMapperTest {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-07-28T10:00:00Z").withOffsetSameInstant(ZoneOffset.UTC);

    @Mock
    private JudicialResultPromptParser promptParser;

    @InjectMocks
    private CPVersionEntityMapper mapper;

    @Test
    void toCaseHearingEntity_should_mapCaseUrnCourtHouseAndHearingDate() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getCaseUrn()).isEqualTo(CASE_URN);
        assertThat(result.getHearingId()).isEqualTo(HEARING_ID);
        assertThat(result.getCourtHouseCode()).isEqualTo("B01LY");
        assertThat(result.getCourtHouseName()).isEqualTo("Leeds Crown Court");
        assertThat(result.getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(result.getHearingOutcome()).isNull();
        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void toCaseMarkerEntities_should_mapEachMarkerCode() {
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of(CaseMarker.builder().markerTypeCode("DomesticViolence").build()))
                .defendants(List.of())
                .build();

        final List<CPCaseMarkerEntity> result = mapper.toCaseMarkerEntities(prosecutionCase, CASE_HEARING_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(result.get(0).getCode()).isEqualTo("DomesticViolence");
    }

    @Test
    void eligibleResults_should_includeDirectOffenceResults() {
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).containsExactly(result);
    }

    @Test
    void eligibleResults_should_includeLinkedCourtApplicationResults_whenMasterDefendantIdMatches() {
        final JudicialResult applicationResult = JudicialResult.builder().cjsCode("APP1").judicialResultPrompts(List.of()).build();
        final JudicialResult linkedOffenceResult = JudicialResult.builder().cjsCode("APP2").judicialResultPrompts(List.of()).build();
        final Offence linkedOffence = Offence.builder().judicialResults(List.of(linkedOffenceResult)).build();
        final CourtApplication matching = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .respondents(List.of(Respondent.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()))
                .courtApplicationCases(List.of(CourtApplicationCase.builder().offences(List.of(linkedOffence)).build()))
                .judicialResults(List.of(applicationResult))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(matching)).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).containsExactlyInAnyOrder(applicationResult, linkedOffenceResult);
    }

    @Test
    void eligibleResults_should_excludeCourtApplication_whenMasterDefendantIdDoesNotMatch() {
        final CourtApplication other = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e60")
                .respondents(List.of(Respondent.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").build()))
                .courtApplicationCases(List.of())
                .judicialResults(List.of(JudicialResult.builder().cjsCode("X").judicialResultPrompts(List.of()).build()))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(other)).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).isEmpty();
    }

    private ProsecutionCase minimalProsecutionCase() {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of())
                .build();
    }
}