package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.AllocationDecision;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplicationCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtOrder;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtOrderOffence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.DefendantAttendance;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.DefendantCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.DefendantJudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.AttendanceDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Verdict;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.VerdictType;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.IndicatedPlea;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.LocalJusticeArea;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PleaDetails;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDetails;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.MasterDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ApplicationParty;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ApplicationType;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPNextHearingEmbeddable;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CPHearingResultEntityMapperTest {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-07-28T10:00:00Z").withOffsetSameInstant(ZoneOffset.UTC);
    private static final OffsetDateTime EXPIRES_AT = CREATED_AT.plusDays(30);
    private static final Instant SHARED_TIME = Instant.parse("2026-07-28T09:33:21Z");

    @Mock
    private CPJudicialResultPromptParser promptParser;

    @InjectMocks
    private CPHearingResultEntityMapper mapper;

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
    void toCaseHearingEntity_should_mapHearingType_whenPresent() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .type(HearingDetailsResponse.HearingType.builder()
                        .id("4a0e892d-c0c5-3c51-95b8-704d8c781776").description("First hearing").build())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getHearingType()).isEqualTo("First hearing");
    }

    @Test
    void toCaseHearingEntity_should_leaveHearingTypeNull_whenAbsent() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getHearingType()).isNull();
    }

    @Test
    void toCaseHearingEntity_should_mapLjaNameAndCourtAddress_whenPresent() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .courtCentre(CourtCentre.builder()
                        .code("B01LY").name("Leeds Crown Court")
                        .lja(LocalJusticeArea.builder().ljaName("South East London Magistrates' Court").build())
                        .address(Address.builder()
                                .address1("1 Court Street").address2("Suite 2").address3("Town")
                                .address4("County").address5("Country").postcode("SE1 1AA")
                                .build())
                        .build())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getLjaName()).isEqualTo("South East London Magistrates' Court");
        assertThat(result.getCourtAddressLine1()).isEqualTo("1 Court Street");
        assertThat(result.getCourtAddressLine2()).isEqualTo("Suite 2");
        assertThat(result.getCourtAddressLine3()).isEqualTo("Town");
        assertThat(result.getCourtAddressLine4()).isEqualTo("County");
        assertThat(result.getCourtAddressLine5()).isEqualTo("Country");
        assertThat(result.getCourtPostCode()).isEqualTo("SE1 1AA");
    }

    @Test
    void toCaseHearingEntity_should_leaveLjaNameAndCourtAddressNull_whenNoCourtCentre() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getLjaName()).isNull();
        assertThat(result.getCourtAddressLine1()).isNull();
        assertThat(result.getCourtPostCode()).isNull();
    }

    @Test
    void toCaseHearingEntity_should_leaveCourtHouseFieldsNull_whenNoCourtCentre() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getCourtHouseCode()).isNull();
        assertThat(result.getCourtHouseName()).isNull();
        assertThat(result.getCourtHouseId()).isNull();
        assertThat(result.getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
    }

    @Test
    void toCaseHearingEntity_should_mapCourtHouseId_whenPresent() {
        final UUID courtHouseId = UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26");
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().id(courtHouseId.toString()).code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of())
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getCourtHouseId()).isEqualTo(courtHouseId);
    }

    @Test
    void toCaseHearingEntity_should_leaveHearingDateNull_whenNoHearingDays() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of())
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getHearingDate()).isNull();
        assertThat(result.getCourtHouseCode()).isEqualTo("B01LY");
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
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
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
    void eligibleResults_should_includeCourtOrderOffenceResults_onResentencingApplication() {
        final JudicialResult applicationResult = JudicialResult.builder().cjsCode("APP1").judicialResultPrompts(List.of()).build();
        final JudicialResult courtOrderOffenceResult = JudicialResult.builder().cjsCode("CJ03522").judicialResultPrompts(List.of()).build();
        final Offence courtOrderOffence = Offence.builder().offenceCode("CJ03522").judicialResults(List.of(courtOrderOffenceResult)).build();
        final CourtApplication resentencing = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .courtApplicationCases(List.of())
                .courtOrder(CourtOrder.builder().courtOrderOffences(List.of(CourtOrderOffence.builder().offence(courtOrderOffence).build())).build())
                .judicialResults(List.of(applicationResult))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(resentencing)).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).containsExactlyInAnyOrder(applicationResult, courtOrderOffenceResult);
    }

    @Test
    void eligibleResults_should_excludeCourtApplication_whenMasterDefendantIdDoesNotMatch() {
        final CourtApplication other = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e60")
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").build()).build())
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

    @Test
    void toWriteBundle_should_setSurrogatePkAndCaseHearingIdAndTimestamps() {
        final Defendant defendant = minimalDefendant();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getCpVersionPk()).isNotNull();
        assertThat(bundle.version().getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(bundle.version().getDefendantId()).isEqualTo(DEFENDANT_ID);
        assertThat(bundle.version().getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(bundle.version().getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(bundle.version().getEventId()).isNull();
    }

    @Test
    void toWriteBundle_should_mapPersonDetailsAndAddress() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder()
                                .title("Mr").firstName("John").middleName("Q").lastName("Doe")
                                .dateOfBirth(LocalDate.of(1990, 1, 31))
                                .address(Address.builder().address1("1 Example Street").address2("Townville")
                                        .address3("Countyshire").postcode("AB1 2CD").build())
                                .gender("MALE").nationalityDescription("British")
                                .build())
                        .build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getTitle()).isEqualTo("Mr");
        assertThat(bundle.version().getFirstName()).isEqualTo("John");
        assertThat(bundle.version().getMiddleName()).isEqualTo("Q");
        assertThat(bundle.version().getLastName()).isEqualTo("Doe");
        assertThat(bundle.version().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 31));
        assertThat(bundle.version().getAddressLine1()).isEqualTo("1 Example Street");
        assertThat(bundle.version().getAddressLine2()).isEqualTo("Townville");
        assertThat(bundle.version().getAddressLine3()).isEqualTo("Countyshire");
        assertThat(bundle.version().getAddressLine4()).isNull();
        assertThat(bundle.version().getPostCode()).isEqualTo("AB1 2CD");
        assertThat(bundle.version().getGender()).isEqualTo("MALE");
        assertThat(bundle.version().getNationality()).isEqualTo("British");
    }

    @Test
    void toWriteBundle_should_leavePiiNull_whenNoPersonDetails() {
        final CPEntitySet bundle = mapper.toWriteBundle(minimalDefendant(),
                HearingDetail.builder().courtApplications(List.of()).build(), CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getFirstName()).isNull();
        assertThat(bundle.version().getDateOfBirth()).isNull();
        assertThat(bundle.version().getGender()).isNull();
        assertThat(bundle.version().getNationality()).isNull();
    }

    @Test
    void toWriteBundle_should_mapNextHearing_fromRealCPPayloadShape() {
        final HearingDetailsResponse.NextHearing nextHearing = HearingDetailsResponse.NextHearing.builder()
                .bookingReference("41a6176a-4304-4986-91b6-588969195c56")
                .listedStartDateTime(Instant.parse("2026-07-31T09:00:00Z"))
                .courtCentre(CourtCentre.builder()
                        .id("f8254db1-1683-483e-afb3-b87fde5a0a26")
                        .code("B01LY00")
                        .name("Lavender Hill Magistrates' Court")
                        .build())
                .build();
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").judicialResultPrompts(List.of()).nextHearing(nextHearing).build();
        final Offence offence = Offence.builder().judicialResults(List.of(result)).build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(Defendant.builder()
                        .id(DEFENDANT_ID.toString())
                        .personDefendant(PersonDefendant.builder().build())
                        .offences(List.of(offence))
                        .build()))
                .build();
        final HearingDetail hearing = HearingDetail.builder()
                .courtApplications(List.of())
                .prosecutionCases(List.of(prosecutionCase))
                .build();

        final CPEntitySet bundle = mapper.toWriteBundle(minimalDefendant(), hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        final CPNextHearingEmbeddable mapped = bundle.version().getNextHearing();
        assertThat(mapped).isNotNull();
        assertThat(mapped.getDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(mapped.getTime()).isEqualTo("09:00");
        assertThat(mapped.getCourtHouseId()).isEqualTo(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        assertThat(mapped.getCourtHouseCode()).isEqualTo("B01LY00");
        assertThat(mapped.getCourtHouseName()).isEqualTo("Lavender Hill Magistrates' Court");
        assertThat(mapped.getId()).isEqualTo(UUID.fromString("41a6176a-4304-4986-91b6-588969195c56"));
    }

    @Test
    void toWriteBundle_should_mapCustodyType_whenCustodialEstablishmentPresent() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder()
                        .custodialEstablishment(CustodialEstablishment.builder().name("HMP Dovegate").custody("Prison").build())
                        .build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getCustodyLocation()).isEqualTo("HMP Dovegate");
        assertThat(bundle.version().getCustodyType()).isEqualTo("Prison");
    }

    @Test
    void toWriteBundle_should_leaveCustodyTypeNull_whenNoCustodialEstablishment() {
        final CPEntitySet bundle = mapper.toWriteBundle(minimalDefendant(),
                HearingDetail.builder().courtApplications(List.of()).build(), CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getCustodyType()).isNull();
    }

    // Confirmed against DefendantContextBaseService.js/RegisterFragmentService.js: OFFENCE and
    // APPLICATION level results are pushed into the same combined array as DEFENDANT/CASE level
    // ones, and publishedForNows is filtered out of that whole array before any level reads from
    // it — the exclusion is not specific to defendantResults/caseResults.
    @Test
    void toWriteBundle_should_excludePublishedForNows_fromDirectAndLinkedOffenceResults() {
        final JudicialResult keep = JudicialResult.builder().cjsCode("KEEP").label("Keep").judicialResultPrompts(List.of()).build();
        final JudicialResult drop = JudicialResult.builder().cjsCode("DROP").label("Drop").publishedForNows(true).judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().offenceCode("TH68001").judicialResults(List.of(keep, drop)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.judicialResults()).extracting(CPJudicialResultEntity::getResultCode).containsExactly("KEEP");
    }

    @Test
    void toWriteBundle_should_excludePublishedForNows_fromCourtApplicationOwnResults() {
        final JudicialResult keep = JudicialResult.builder().cjsCode("KEEP").label("Keep").judicialResultPrompts(List.of()).build();
        final JudicialResult drop = JudicialResult.builder().cjsCode("DROP").label("Drop").publishedForNows(true).judicialResultPrompts(List.of()).build();
        final CourtApplication application = CourtApplication.builder()
                .id(UUID.randomUUID().toString())
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .courtApplicationCases(List.of())
                .judicialResults(List.of(keep, drop))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(application)).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.judicialResults()).extracting(CPJudicialResultEntity::getResultCode).containsExactly("KEEP");
    }

    @Test
    void toWriteBundle_should_mapDirectOffenceAndItsJudicialResultAndPrompts_withSurrogateOffenceId() {
        when(promptParser.fineAmount(any())).thenReturn(null);
        final JudicialResultPrompt prompt = JudicialResultPrompt.builder().promptReference("prisonOrganisationName")
                .value("HMP Dovegate").label("Prison organisation name").type("NAMEADDRESS").build();
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").label("Imprisonment").resultText("RI - Remanded in custody")
                .category("FINAL").postHearingCustodyStatus("A")
                .isFinancialResult(false).isConvictedResult(true)
                .judicialResultPrompts(List.of(prompt))
                .build();
        final Offence offence = Offence.builder().offenceCode("TH68001").offenceTitle("Theft").wording("Stole a thing")
                .listingNumber(1).judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences()).hasSize(1);
        final CPOffenceEntity offenceEntity = bundle.offences().get(0);
        assertThat(offenceEntity.getId()).isNotNull();
        assertThat(offenceEntity.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        assertThat(offenceEntity.getCourtApplicationId()).isNull();
        assertThat(offenceEntity.getCode()).isEqualTo("TH68001");
        assertThat(offenceEntity.getTitle()).isEqualTo("Theft");
        assertThat(offenceEntity.getWording()).isEqualTo("Stole a thing");
        assertThat(bundle.judicialResults()).hasSize(1);
        final CPJudicialResultEntity resultEntity = bundle.judicialResults().get(0);
        assertThat(resultEntity.getOffenceId()).isEqualTo(offenceEntity.getId());
        assertThat(resultEntity.getCourtApplicationId()).isNull();
        assertThat(resultEntity.getResultCode()).isEqualTo("1200");
        assertThat(resultEntity.getResultText()).isEqualTo("RI - Remanded in custody");
        assertThat(resultEntity.getCategory()).isEqualTo("FINAL");
        assertThat(resultEntity.getFinancial()).isFalse();
        assertThat(resultEntity.getConvicted()).isTrue();
        assertThat(bundle.judicialResultPrompts()).hasSize(1);
        assertThat(bundle.judicialResultPrompts().get(0).getJudicialResultId()).isEqualTo(resultEntity.getId());
        assertThat(bundle.judicialResultPrompts().get(0).getPromptReference()).isEqualTo("prisonOrganisationName");
        assertThat(bundle.judicialResultPrompts().get(0).getLabel()).isEqualTo("Prison organisation name");
        assertThat(bundle.judicialResultPrompts().get(0).getType()).isEqualTo("NAMEADDRESS");
    }

    @Test
    void toWriteBundle_should_mapVerdictAndOffenceLegislation_whenPresent() {
        final Offence offence = Offence.builder()
                .offenceCode("TH68001")
                .offenceLegislation("Contrary to section 1(1) and 7 of the Theft Act 1968.")
                .verdict(Verdict.builder().verdictType(VerdictType.builder().verdictCode("G").description("Found guilty").build()).build())
                .allocationDecision(AllocationDecision.builder().motReasonDescription("Summarily").build())
                .indicatedPlea(IndicatedPlea.builder().indicatedPleaValue("GUILTY").build())
                .judicialResults(List.of())
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        // Sourced from verdictType.description, not verdictType.verdictCode — legacy's own
        // OffenceMapper.js naming quirk, mirrored deliberately (see toVerdict's comment).
        assertThat(bundle.offences().get(0).getVerdict()).isEqualTo("Found guilty");
        assertThat(bundle.offences().get(0).getOffenceLegislation())
                .isEqualTo("Contrary to section 1(1) and 7 of the Theft Act 1968.");
        assertThat(bundle.offences().get(0).getAllocationDecision()).isEqualTo("Summarily");
        assertThat(bundle.offences().get(0).getIndicatedPleaValue()).isEqualTo("GUILTY");
    }

    @Test
    void toWriteBundle_should_leaveVerdictNull_whenNoVerdict() {
        final Offence offence = Offence.builder().offenceCode("TH68001").judicialResults(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences().get(0).getVerdict()).isNull();
        assertThat(bundle.offences().get(0).getOffenceLegislation()).isNull();
        assertThat(bundle.offences().get(0).getAllocationDecision()).isNull();
        assertThat(bundle.offences().get(0).getIndicatedPleaValue()).isNull();
    }

    @Test
    void toWriteBundle_should_mapPostHearingCustodyStatus_toFirstNonNotApplicableCaseLevelResult() {
        final JudicialResult notApplicable = JudicialResult.builder()
                .postHearingCustodyStatus("Not Applicable").judicialResultPrompts(List.of()).build();
        final JudicialResult meaningful = JudicialResult.builder()
                .postHearingCustodyStatus("Bailed").judicialResultPrompts(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .defendantCaseJudicialResults(List.of(notApplicable, meaningful))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getPostHearingCustodyStatus()).isEqualTo("Bailed");
    }

    @Test
    void toWriteBundle_should_defaultPostHearingCustodyStatus_whenNoneMeaningful() {
        final Defendant defendant = minimalDefendant();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getPostHearingCustodyStatus()).isEqualTo("Not Applicable");
    }

    @Test
    void toWriteBundle_should_persistCaseResults_fromDefendantCaseJudicialResults() {
        final JudicialResult caseResult = JudicialResult.builder()
                .cjsCode("C1").label("Costs").judicialResultPrompts(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .defendantCaseJudicialResults(List.of(caseResult))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.judicialResults()).hasSize(1);
        final CPJudicialResultEntity result = bundle.judicialResults().get(0);
        assertThat(result.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        assertThat(result.getLevel()).isEqualTo("C");
        assertThat(result.getOffenceId()).isNull();
        assertThat(result.getCourtApplicationId()).isNull();
        assertThat(result.getResultCode()).isEqualTo("C1");
    }

    @Test
    void toWriteBundle_should_persistDefendantResults_matchedByMasterDefendantId() {
        final JudicialResult defendantResult = JudicialResult.builder()
                .cjsCode("D1").label("Collection order").judicialResultPrompts(List.of()).build();
        final JudicialResult anotherDefendantsResult = JudicialResult.builder()
                .cjsCode("D2").label("Should not be included").judicialResultPrompts(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder()
                .courtApplications(List.of())
                .defendantJudicialResults(List.of(
                        DefendantJudicialResult.builder().masterDefendantId(MASTER_DEFENDANT_ID).judicialResult(defendantResult).build(),
                        DefendantJudicialResult.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").judicialResult(anotherDefendantsResult).build()))
                .build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.judicialResults()).hasSize(1);
        final CPJudicialResultEntity result = bundle.judicialResults().get(0);
        assertThat(result.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        assertThat(result.getLevel()).isEqualTo("D");
        assertThat(result.getResultCode()).isEqualTo("D1");
    }

    // Confirmed against a real hearing fixture: every hearing.defendantJudicialResults entry
    // observed so far is publishedForNows=true, meaning it never actually reaches the register —
    // RegisterFragmentService.js's filterJudicialResultsApplicableForRegisters excludes it before
    // any level split, same rule CPResultsPcrFilter.excludePublishedForNows already applies to
    // the PCR-required gate.
    @Test
    void toWriteBundle_should_excludePublishedForNows_fromDefendantAndCaseResults() {
        final JudicialResult publishedForNowsResult = JudicialResult.builder()
                .cjsCode("D1").label("Collection order").publishedForNows(true).judicialResultPrompts(List.of()).build();
        final JudicialResult caseResult = JudicialResult.builder()
                .cjsCode("C1").label("Costs").publishedForNows(true).judicialResultPrompts(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .defendantCaseJudicialResults(List.of(caseResult))
                .build();
        final HearingDetail hearing = HearingDetail.builder()
                .courtApplications(List.of())
                .defendantJudicialResults(List.of(
                        DefendantJudicialResult.builder().masterDefendantId(MASTER_DEFENDANT_ID).judicialResult(publishedForNowsResult).build()))
                .build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.judicialResults()).isEmpty();
    }

    @Test
    void toWriteBundle_should_mapDefendantAppearanceDetails_whenAttendanceMatchesSittingDay() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder()
                .courtApplications(List.of())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-08-11").build()))
                .defendantAttendance(List.of(DefendantAttendance.builder()
                        .defendantId(DEFENDANT_ID.toString())
                        .attendanceDays(List.of(AttendanceDay.builder().day("2026-08-11").attendanceType("IN_PERSON").build()))
                        .build()))
                .build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getDefendantAppearanceDetails()).isEqualTo("In person");
    }

    @Test
    void toWriteBundle_should_translateEveryAttendanceType_toItsLegacyDisplayString() {
        final HearingDetail videoHearing = hearingWithAttendance("BY_VIDEO");
        final HearingDetail notPresentHearing = hearingWithAttendance("NOT_PRESENT");
        final HearingDetail unrecognisedHearing = hearingWithAttendance("SOMETHING_ELSE");

        assertThat(mapper.toWriteBundle(minimalDefendant(), videoHearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT)
                .version().getDefendantAppearanceDetails()).isEqualTo("By video link");
        assertThat(mapper.toWriteBundle(minimalDefendant(), notPresentHearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT)
                .version().getDefendantAppearanceDetails()).isEqualTo("Not present");
        assertThat(mapper.toWriteBundle(minimalDefendant(), unrecognisedHearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT)
                .version().getDefendantAppearanceDetails()).isNull();
    }

    private HearingDetail hearingWithAttendance(final String attendanceType) {
        return HearingDetail.builder()
                .courtApplications(List.of())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-08-11").build()))
                .defendantAttendance(List.of(DefendantAttendance.builder()
                        .defendantId(DEFENDANT_ID.toString())
                        .attendanceDays(List.of(AttendanceDay.builder().day("2026-08-11").attendanceType(attendanceType).build()))
                        .build()))
                .build();
    }

    // Legacy's own algorithm has a real bug here (`=` instead of `===`, always matching the
    // first attendance entry) — this proves the fix: a SECOND defendant with no matching
    // attendance entry of their own must not inherit the FIRST defendant's appearance details.
    @Test
    void toWriteBundle_should_notMatchAnotherDefendantsAttendance_forMultiDefendantHearing() {
        final Defendant secondDefendant = Defendant.builder()
                .id("44444444-4444-4444-4444-444444444444")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder()
                .courtApplications(List.of())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-08-11").build()))
                .defendantAttendance(List.of(DefendantAttendance.builder()
                        .defendantId(DEFENDANT_ID.toString())
                        .attendanceDays(List.of(AttendanceDay.builder().day("2026-08-11").attendanceType("IN_PERSON").build()))
                        .build()))
                .build();

        final CPEntitySet bundle = mapper.toWriteBundle(secondDefendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getDefendantAppearanceDetails()).isNull();
    }

    @Test
    void toWriteBundle_should_defaultDefendantAppearanceDetailsToNull_whenNoAttendanceRecorded() {
        final Defendant defendant = minimalDefendant();
        final HearingDetail hearing = HearingDetail.builder()
                .courtApplications(List.of())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-08-11").build()))
                .build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getDefendantAppearanceDetails()).isNull();
    }

    @Test
    void toWriteBundle_should_mapSourceOffenceId_whenPresent() {
        final Offence offence = Offence.builder()
                .id("9f4752be-7c1b-4eb5-9940-a26c5ae37ebe")
                .offenceCode("TH68001")
                .judicialResults(List.of())
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences().get(0).getSourceOffenceId())
                .isEqualTo(UUID.fromString("9f4752be-7c1b-4eb5-9940-a26c5ae37ebe"));
    }

    @Test
    void toWriteBundle_should_leaveSourceOffenceIdNull_whenAbsent() {
        final Offence offence = Offence.builder().offenceCode("TH68001").judicialResults(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences().get(0).getSourceOffenceId()).isNull();
    }

    @Test
    void toWriteBundle_should_mapPleaValueAndPleaDate_whenPresent() {
        final Offence offence = Offence.builder()
                .offenceCode("TH68001")
                .plea(PleaDetails.builder().pleaValue("GUILTY").pleaDate(LocalDate.of(2026, 7, 31)).build())
                .judicialResults(List.of())
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences().get(0).getPleaValue()).isEqualTo("GUILTY");
        assertThat(bundle.offences().get(0).getPleaDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void toWriteBundle_should_leavePleaNull_whenAbsent() {
        final Offence offence = Offence.builder().offenceCode("TH68001").judicialResults(List.of()).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences().get(0).getPleaValue()).isNull();
        assertThat(bundle.offences().get(0).getPleaDate()).isNull();
    }

    @Test
    void toWriteBundle_should_mapLinkedCourtApplicationAndItsOffenceAndOwnResult() {
        when(promptParser.fineAmount(any())).thenReturn(null);
        final JudicialResult linkedOffenceResult = JudicialResult.builder().cjsCode("LINK1").judicialResultPrompts(List.of()).build();
        final Offence linkedOffence = Offence.builder().offenceCode("LINKOFF").judicialResults(List.of(linkedOffenceResult)).build();
        final JudicialResult applicationResult = JudicialResult.builder().cjsCode("APP1").judicialResultPrompts(List.of()).build();
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .applicationReference("REF1").type(ApplicationType.builder().type("Bail").build())
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .courtApplicationCases(List.of(CourtApplicationCase.builder().offences(List.of(linkedOffence)).build()))
                .judicialResults(List.of(applicationResult))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(application)).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.courtApplications()).hasSize(1);
        final CPCourtApplicationEntity applicationEntity = bundle.courtApplications().get(0);
        assertThat(applicationEntity.getId()).isNotNull();
        assertThat(applicationEntity.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        assertThat(applicationEntity.getSourceApplicationId())
                .isEqualTo(UUID.fromString("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f"));
        assertThat(applicationEntity.getReference()).isEqualTo("REF1");
        assertThat(bundle.offences()).hasSize(1);
        assertThat(bundle.offences().get(0).getCourtApplicationId()).isEqualTo(applicationEntity.getId());
        assertThat(bundle.offences().get(0).getVersionPk()).isNull();
        assertThat(bundle.judicialResults()).hasSize(2);
        assertThat(bundle.judicialResults()).extracting("resultCode").containsExactlyInAnyOrder("LINK1", "APP1");
        final CPJudicialResultEntity applicationLevelResult = bundle.judicialResults().stream()
                .filter(r -> "APP1".equals(r.getResultCode())).findFirst().orElseThrow();
        assertThat(applicationLevelResult.getCourtApplicationId()).isEqualTo(applicationEntity.getId());
        assertThat(applicationLevelResult.getOffenceId()).isNull();
    }

    @Test
    void toWriteBundle_should_mapCourtOrderOffence_onResentencingApplication() {
        when(promptParser.fineAmount(any())).thenReturn(null);
        final JudicialResult courtOrderOffenceResult = JudicialResult.builder().cjsCode("CJ03522").judicialResultPrompts(List.of()).build();
        final Offence courtOrderOffence = Offence.builder().offenceCode("CJ03522")
                .wording("Original CaseURN: YX123927526, Re-sentenced Original code : TH68023, Original details: Robbery")
                .judicialResults(List.of(courtOrderOffenceResult)).build();
        final CourtApplication resentencing = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .applicationReference("REF1").type(ApplicationType.builder().type("Resentenced").build())
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .courtApplicationCases(List.of())
                .courtOrder(CourtOrder.builder().courtOrderOffences(List.of(CourtOrderOffence.builder().offence(courtOrderOffence).build())).build())
                .judicialResults(List.of())
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(resentencing)).build();

        final CPEntitySet bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.courtApplications()).hasSize(1);
        final CPCourtApplicationEntity applicationEntity = bundle.courtApplications().get(0);
        assertThat(bundle.offences()).hasSize(1);
        final CPOffenceEntity offenceEntity = bundle.offences().get(0);
        assertThat(offenceEntity.getCode()).isEqualTo("CJ03522");
        assertThat(offenceEntity.getCourtApplicationId()).isEqualTo(applicationEntity.getId());
        assertThat(bundle.judicialResults()).hasSize(1);
        assertThat(bundle.judicialResults().get(0).getResultCode()).isEqualTo("CJ03522");
        assertThat(bundle.judicialResults().get(0).getOffenceId()).isEqualTo(offenceEntity.getId());
    }

    @Test
    void toWriteBundle_should_generateDistinctCourtApplicationIds_whenTwoDefendantsShareTheSameApplication() {
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .applicationReference("REF1").type(ApplicationType.builder().type("Bail").build())
                .subject(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .courtApplicationCases(List.of())
                .judicialResults(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(application)).build();
        final Defendant defendantA = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final Defendant defendantB = Defendant.builder()
                .id("44444444-4444-4444-4444-444444444444")
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();

        final CPEntitySet bundleA = mapper.toWriteBundle(defendantA, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);
        final CPEntitySet bundleB = mapper.toWriteBundle(defendantB, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundleA.courtApplications().get(0).getId())
                .isNotEqualTo(bundleB.courtApplications().get(0).getId());
        assertThat(bundleA.courtApplications().get(0).getVersionPk()).isEqualTo(bundleA.version().getCpVersionPk());
        assertThat(bundleB.courtApplications().get(0).getVersionPk()).isEqualTo(bundleB.version().getCpVersionPk());
    }

    @Test
    void toCaseHearingEntity_should_mapCaseUrn_whenGivenPlainCaseUrnString() {
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity("APP-REF-1", hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getCaseUrn()).isEqualTo("APP-REF-1");
        assertThat(result.getHearingId()).isEqualTo(HEARING_ID);
        assertThat(result.getCourtHouseCode()).isEqualTo("B01LY");
    }

    @Test
    void toWriteBundle_should_defaultDefendantType_toDefendant_whenNotGivenExplicitly() {
        final CPEntitySet bundle = mapper.toWriteBundle(minimalDefendant(),
                HearingDetail.builder().courtApplications(List.of()).build(), CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getDefendantType()).isEqualTo("Defendant");
    }

    @Test
    void toWriteBundle_should_setDefendantType_whenGivenExplicitly() {
        final CPEntitySet bundle = mapper.toWriteBundle(minimalDefendant(),
                HearingDetail.builder().courtApplications(List.of()).build(), CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT,
                "Respondent");

        assertThat(bundle.version().getDefendantType()).isEqualTo("Respondent");
    }

    @Test
    void applicationOnlyDefendant_should_buildDefendant_whenSingleDefendantCase() {
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .subject(ApplicationParty.builder()
                        .masterDefendant(MasterDefendant.builder()
                                .masterDefendantId(MASTER_DEFENDANT_ID)
                                .isYouth(false)
                                .personDefendant(PersonDefendant.builder()
                                        .personDetails(PersonDetails.builder().firstName("Chase").lastName("Von").build())
                                        .build())
                                .defendantCase(List.of(DefendantCase.builder()
                                        .caseId("case-A").caseReference("CV1").defendantId(DEFENDANT_ID.toString()).build()))
                                .build())
                        .build())
                .courtApplicationCases(List.of())
                .build();

        final Optional<Defendant> result = mapper.applicationOnlyDefendant(application);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(DEFENDANT_ID.toString());
        assertThat(result.get().getMasterDefendantId()).isEqualTo(MASTER_DEFENDANT_ID);
        assertThat(result.get().getIsYouth()).isFalse();
        assertThat(result.get().getPersonDefendant().getPersonDetails().getFirstName()).isEqualTo("Chase");
        assertThat(result.get().getOffences()).isEmpty();
    }

    @Test
    void applicationOnlyDefendant_should_returnEmpty_whenNoSubjectMasterDefendant() {
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .subject(ApplicationParty.builder().build())
                .build();

        assertThat(mapper.applicationOnlyDefendant(application)).isEmpty();
    }

    @Test
    void applicationOnlyDefendant_should_resolveDefendantId_byMatchingProsecutionCaseId_whenMultipleDefendantCases() {
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .subject(ApplicationParty.builder()
                        .masterDefendant(MasterDefendant.builder()
                                .masterDefendantId(MASTER_DEFENDANT_ID)
                                .personDefendant(PersonDefendant.builder().build())
                                .defendantCase(List.of(
                                        DefendantCase.builder().caseId("case-A").defendantId("11111111-1111-1111-1111-111111111111").build(),
                                        DefendantCase.builder().caseId("case-B").defendantId("22222222-2222-2222-2222-222222222222").build()))
                                .build())
                        .build())
                .courtApplicationCases(List.of(CourtApplicationCase.builder().prosecutionCaseId("case-B").build()))
                .build();

        final Optional<Defendant> result = mapper.applicationOnlyDefendant(application);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("22222222-2222-2222-2222-222222222222");
    }

    @Test
    void applicationOnlyDefendant_should_returnEmpty_whenMultipleDefendantCasesAndNoMatchingProsecutionCaseId() {
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .subject(ApplicationParty.builder()
                        .masterDefendant(MasterDefendant.builder()
                                .masterDefendantId(MASTER_DEFENDANT_ID)
                                .personDefendant(PersonDefendant.builder().build())
                                .defendantCase(List.of(
                                        DefendantCase.builder().caseId("case-A").defendantId("11111111-1111-1111-1111-111111111111").build(),
                                        DefendantCase.builder().caseId("case-B").defendantId("22222222-2222-2222-2222-222222222222").build()))
                                .build())
                        .build())
                .courtApplicationCases(List.of(CourtApplicationCase.builder().prosecutionCaseId("case-C").build()))
                .build();

        assertThat(mapper.applicationOnlyDefendant(application)).isEmpty();
    }

    // Ports PrisonCourtRegisterHandler.getDefendantType (progression-command-handler/.../
    // PrisonCourtRegisterHandler.java:149-166) — applicant.masterDefendant present and the
    // application is not flagged as an appeal.
    @Test
    void defendantType_should_returnApplicant_whenApplicantHasMasterDefendant_andNotAnAppeal() {
        final CourtApplication application = CourtApplication.builder()
                .type(ApplicationType.builder().appealFlag(false).applicantAppellantFlag(false).build())
                .applicant(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Applicant");
    }

    @Test
    void defendantType_should_returnAppellant_whenApplicantHasMasterDefendant_andBothAppealFlagsTrue() {
        final CourtApplication application = CourtApplication.builder()
                .type(ApplicationType.builder().appealFlag(true).applicantAppellantFlag(true).build())
                .applicant(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Appellant");
    }

    @Test
    void defendantType_should_returnApplicant_whenApplicantHasMasterDefendant_andOnlyOneAppealFlagTrue() {
        final CourtApplication application = CourtApplication.builder()
                .type(ApplicationType.builder().appealFlag(true).applicantAppellantFlag(false).build())
                .applicant(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build())
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Applicant");
    }

    // Applicant branch never checks whose masterDefendant it is — a literal port of the
    // legacy quirk (design doc 2026-09-02 §2 point 3), not "fixed".
    @Test
    void defendantType_should_returnApplicant_whenApplicantMasterDefendantBelongsToSomeoneElse() {
        final CourtApplication application = CourtApplication.builder()
                .type(ApplicationType.builder().appealFlag(false).applicantAppellantFlag(false).build())
                .applicant(ApplicationParty.builder().masterDefendant(MasterDefendant.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").build()).build())
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Applicant");
    }

    @Test
    void defendantType_should_returnRespondent_whenNoApplicantMasterDefendant_andMasterDefendantIdMatchesRespondent() {
        final CourtApplication application = CourtApplication.builder()
                .applicant(ApplicationParty.builder().build())
                .respondents(List.of(ApplicationParty.builder()
                        .masterDefendant(MasterDefendant.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()).build()))
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Respondent");
    }

    @Test
    void defendantType_should_returnApplicant_whenNeitherApplicantNorAnyRespondentMatches() {
        final CourtApplication application = CourtApplication.builder()
                .applicant(ApplicationParty.builder().build())
                .respondents(List.of(ApplicationParty.builder()
                        .masterDefendant(MasterDefendant.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").build()).build()))
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Applicant");
    }

    // A respondent can be a prosecuting authority, not a defendant — CourtApplicationParty (CP's
    // own model) has masterDefendant/prosecutingAuthority as independent nullable fields on the
    // same party type. Must not NPE, and must not falsely match.
    @Test
    void defendantType_should_returnApplicant_whenARespondentHasNoMasterDefendantAtAll() {
        final CourtApplication application = CourtApplication.builder()
                .applicant(ApplicationParty.builder().build())
                .respondents(List.of(
                        ApplicationParty.builder().build(),
                        ApplicationParty.builder()
                                .masterDefendant(MasterDefendant.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").build()).build()))
                .build();

        assertThat(mapper.defendantType(application, MASTER_DEFENDANT_ID)).isEqualTo("Applicant");
    }

    private ProsecutionCase minimalProsecutionCase() {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of())
                .build();
    }

    private Defendant minimalDefendant() {
        return Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
    }
}