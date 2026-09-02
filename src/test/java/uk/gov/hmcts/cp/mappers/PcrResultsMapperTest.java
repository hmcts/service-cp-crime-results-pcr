package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPNextHearingEmbeddable;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.openapi.model.PcrHearingResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PcrResultsMapperTest {

    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID MASTER_DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @InjectMocks
    private PcrResultsMapper mapper;

    private static CPVersionEntity minimalVersion() {
        return CPVersionEntity.builder().cpVersionPk(VERSION_PK).build();
    }

    @Test
    void toPcrHearingResult_should_mapCaseUrnAndCaseMarkers() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().caseUrn("ABCD1234567").hearingId(HEARING_ID).build();
        final CPVersionEntity version = minimalVersion();
        final List<CPCaseMarkerEntity> markers = List.of(
                CPCaseMarkerEntity.builder().code("DomesticViolence").description("Domestic Violence").build());

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, markers, List.of(), List.of(), List.of(), List.of());

        assertThat(result.getProsecutionCase().getCaseURN()).isEqualTo("ABCD1234567");
        assertThat(result.getProsecutionCase().getCaseMarkers()).extracting("description").containsExactly("Domestic Violence");
    }

    @Test
    void toPcrHearingResult_should_mapDefendantIdentityAndPii() {
        final CPVersionEntity version = CPVersionEntity.builder()
                .defendantId(DEFENDANT_ID).masterDefendantId(MASTER_DEFENDANT_ID)
                .title("Mr").firstName("John").middleName("Q").lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 31))
                .addressLine1("1 Example Street").postCode("AB1 2CD")
                .gender("MALE").nationality("British").postHearingCustodyStatus("Not Applicable")
                .build();
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getDefendant().getId()).isEqualTo(DEFENDANT_ID);
        assertThat(result.getDefendant().getMasterDefendantId()).isEqualTo(MASTER_DEFENDANT_ID);
        assertThat(result.getDefendant().getFirstName()).isEqualTo("John");
        assertThat(result.getDefendant().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 31));
        assertThat(result.getDefendant().getAddress().getAddress1()).isEqualTo("1 Example Street");
        assertThat(result.getDefendant().getAddress().getPostCode()).isEqualTo("AB1 2CD");
        assertThat(result.getDefendant().getGender()).isEqualTo("MALE");
        assertThat(result.getDefendant().getNationality()).isEqualTo("British");
        assertThat(result.getDefendant().getPostHearingCustodyStatus()).isEqualTo("Not Applicable");
    }

    @Test
    void toPcrHearingResult_should_mapCustodyLocationNameAndType() {
        final CPVersionEntity version = minimalVersion().toBuilder()
                .custodyLocation("HMP Dovegate").custodyType("Prison").build();
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getCustodyLocation().getName()).isEqualTo("HMP Dovegate");
        assertThat(result.getCustodyLocation().getCustodyType()).isEqualTo("Prison");
    }

    @Test
    void toPcrHearingResult_should_mapHearingDetailsWithCourt() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder()
                .hearingId(HEARING_ID).courtHouseCode("B01LY").courtHouseName("Leeds Crown Court")
                .hearingDate(LocalDate.of(2026, 7, 23)).hearingType("First hearing").jurisdiction("MAGISTRATES").build();
        final CPVersionEntity version = minimalVersion().toBuilder().defendantAppearanceDetails("In person").build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getId()).isEqualTo(HEARING_ID);
        assertThat(result.getHearing().getCourtDetails().getCourt().getCourtHouseCode()).isEqualTo("B01LY");
        assertThat(result.getHearing().getCourtDetails().getCourt().getCourtHouseName()).isEqualTo("Leeds Crown Court");
        assertThat(result.getHearing().getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(result.getHearing().getHearingType()).isEqualTo("First hearing");
        assertThat(result.getHearing().getJurisdiction()).isEqualTo("MAGISTRATES");
        assertThat(result.getHearing().getDefendantAppearanceDetails()).isEqualTo("In person");
    }

    @Test
    void toPcrHearingResult_should_mapCurrentHearingCourtIncludingCourtHouseId() {
        final UUID courtHouseId = UUID.fromString("1a359d44-6b52-3919-b052-065413e4a801");
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder()
                .hearingId(HEARING_ID).courtHouseId(courtHouseId).courtHouseCode("B01LY").courtHouseName("Leeds Crown Court").build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, minimalVersion(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getCourtDetails().getCourt().getCourtHouseId()).isEqualTo(courtHouseId);
    }

    @Test
    void toPcrHearingResult_should_mapSharedTime_whenPresent() {
        final CPVersionEntity version = minimalVersion().toBuilder()
                .sharedTime(OffsetDateTime.parse("2026-07-31T08:33:21.608Z")).build();
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getSharedTime()).isEqualTo(Instant.parse("2026-07-31T08:33:21.608Z"));
    }

    @Test
    void toPcrHearingResult_should_leaveSharedTimeNull_whenAbsent() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, minimalVersion(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getSharedTime()).isNull();
    }

    @Test
    void toPcrHearingResult_should_mapCourtDetailsAddressAndLjaName() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder()
                .hearingId(HEARING_ID)
                .ljaName("South East London Magistrates' Court")
                .courtAddressLine1("1 Court Street").courtAddressLine2("Suite 2").courtPostCode("SE1 1AA")
                .build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, minimalVersion(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getCourtDetails().getLjaName()).isEqualTo("South East London Magistrates' Court");
        assertThat(result.getHearing().getCourtDetails().getCourtAddress().getAddress1()).isEqualTo("1 Court Street");
        assertThat(result.getHearing().getCourtDetails().getCourtAddress().getAddress2()).isEqualTo("Suite 2");
        assertThat(result.getHearing().getCourtDetails().getCourtAddress().getPostCode()).isEqualTo("SE1 1AA");
    }

    @Test
    void toPcrHearingResult_should_leaveCourtDetailsNull_whenNoCourtFactsRecorded() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, minimalVersion(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getCourtDetails()).isNull();
    }

    @Test
    void toPcrHearingResult_should_mapDefendantResultsAndCaseResultsByLevel() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();
        final CPVersionEntity version = minimalVersion();
        final CPJudicialResultEntity defendantResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000071"))
                .versionPk(version.getCpVersionPk()).level("D").resultCode("D1").resultText("Collection order").build();
        final CPJudicialResultEntity caseResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000072"))
                .versionPk(version.getCpVersionPk()).level("C").resultCode("C1").resultText("Costs").build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(),
                List.of(), List.of(), List.of(defendantResult, caseResult), List.of());

        assertThat(result.getDefendant().getResults()).hasSize(1);
        assertThat(result.getProsecutionCase().getResults()).hasSize(1);
    }

    @Test
    void toPcrHearingResult_should_leaveNextHearingNull_whenEmbeddableAbsent() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, minimalVersion(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getNextHearing()).isNull();
    }

    @Test
    void toPcrHearingResult_should_mapNextHearingDateTime_asDateAtMidnightUtc_whenOnlyDateKnown() {
        final CPVersionEntity version = minimalVersion().toBuilder()
                .nextHearing(CPNextHearingEmbeddable.builder().date(LocalDate.of(2026, 8, 1)).build())
                .build();
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getNextHearing()).isNotNull();
        assertThat(result.getHearing().getNextHearing().getDateTime())
                .isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void toPcrHearingResult_should_mapNextHearingDateTime_usingRealTime_whenTimeKnown() {
        final CPVersionEntity version = minimalVersion().toBuilder()
                .nextHearing(CPNextHearingEmbeddable.builder().date(LocalDate.of(2026, 8, 1)).time("10:00").build())
                .build();
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getNextHearing()).isNotNull();
        assertThat(result.getHearing().getNextHearing().getDateTime()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
    }

    @Test
    void toPcrHearingResult_should_mapNextHearingCourtIncludingCourtHouseId() {
        final UUID courtHouseId = UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26");
        final CPVersionEntity version = minimalVersion().toBuilder()
                .nextHearing(CPNextHearingEmbeddable.builder()
                        .date(LocalDate.of(2026, 8, 1))
                        .courtHouseId(courtHouseId)
                        .courtHouseCode("B01LY00")
                        .courtHouseName("Lavender Hill Magistrates' Court")
                        .build())
                .build();
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.getHearing().getNextHearing().getCourt().getCourtHouseId()).isEqualTo(courtHouseId);
        assertThat(result.getHearing().getNextHearing().getCourt().getCourtHouseCode()).isEqualTo("B01LY00");
        assertThat(result.getHearing().getNextHearing().getCourt().getCourtHouseName()).isEqualTo("Lavender Hill Magistrates' Court");
    }

    @Test
    void toPcrHearingResult_should_mapDirectOffenceAndItsResultAndPrompt() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();
        final CPVersionEntity version = minimalVersion();
        final CPOffenceEntity offence = CPOffenceEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000044"))
                .sourceOffenceId(UUID.fromString("00000000-0000-0000-0000-000000000066"))
                .versionPk(version.getCpVersionPk()).code("TH68001").listingNumber(1)
                .verdict("Found guilty").offenceLegislation("Contrary to section 1(1) and 7 of the Theft Act 1968.")
                .allocationDecision("Summarily").indicatedPleaValue("GUILTY").build();
        final CPJudicialResultEntity judicialResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000055"))
                .offenceId(offence.getId()).resultCode("1200")
                .resultText("RI - Remanded in custody\nRemanded in custody until hearing on 12 Jan 2027").build();
        final CPJudicialResultPromptEntity prompt = CPJudicialResultPromptEntity.builder()
                .judicialResultId(judicialResult.getId()).promptReference("prisonOrganisationName")
                .label("Prison organisation name").value("HMP Dovegate").build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(),
                List.of(), List.of(offence), List.of(judicialResult), List.of(prompt));

        assertThat(result.getOffences()).hasSize(1);
        assertThat(result.getOffences().get(0).getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000066"));
        assertThat(result.getOffences().get(0).getCode()).isEqualTo("TH68001");
        assertThat(result.getOffences().get(0).getVerdict()).isEqualTo("Found guilty");
        assertThat(result.getOffences().get(0).getOffenceLegislation())
                .isEqualTo("Contrary to section 1(1) and 7 of the Theft Act 1968.");
        assertThat(result.getOffences().get(0).getAllocationDecision()).isEqualTo("Summarily");
        assertThat(result.getOffences().get(0).getIndicatedPleaValue()).isEqualTo("GUILTY");
        assertThat(result.getOffences().get(0).getResults()).hasSize(1);
        final var mappedResult = result.getOffences().get(0).getResults().get(0);
        assertThat(mappedResult.getResultDescription())
                .isEqualTo("RI - Remanded in custody\nRemanded in custody until hearing on 12 Jan 2027");
        assertThat(mappedResult.getResultTexts()).hasSize(1);
        assertThat(mappedResult.getResultTexts().get(0).getLabel()).isEqualTo("Prison organisation name");
        assertThat(mappedResult.getResultTexts().get(0).getValue()).isEqualTo("HMP Dovegate");
    }

    @Test
    void toPcrHearingResult_should_returnNullResultDescription_whenResultTextIsNull() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();
        final CPVersionEntity version = minimalVersion();
        final CPOffenceEntity offence = CPOffenceEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000047"))
                .versionPk(version.getCpVersionPk()).code("TH68001").listingNumber(1).build();
        final CPJudicialResultEntity judicialResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000058"))
                .offenceId(offence.getId()).resultCode("1200").build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(),
                List.of(), List.of(offence), List.of(judicialResult), List.of());

        final var mappedResult = result.getOffences().get(0).getResults().get(0);
        assertThat(mappedResult.getResultDescription()).isNull();
    }

    @Test
    void toPcrHearingResult_should_mapCourtApplicationWithLinkedOffenceAndOwnResult() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().hearingId(HEARING_ID).build();
        final CPVersionEntity version = minimalVersion();
        final CPCourtApplicationEntity application = CPCourtApplicationEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000066"))
                .versionPk(version.getCpVersionPk()).reference("REF1").type("Bail").build();
        final CPOffenceEntity linkedOffence = CPOffenceEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000067"))
                .courtApplicationId(application.getId()).code("LINKOFF").build();
        final CPJudicialResultEntity applicationResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000068"))
                .courtApplicationId(application.getId()).resultText("APP1").build();

        final PcrHearingResult result = mapper.toPcrHearingResult(caseHearing, version, List.of(),
                List.of(application), List.of(linkedOffence), List.of(applicationResult), List.of());

        assertThat(result.getCourtApplications()).hasSize(1);
        final var mappedApplication = result.getCourtApplications().get(0);
        assertThat(mappedApplication.getReference()).isEqualTo("REF1");
        assertThat(mappedApplication.getResults()).hasSize(1);
        assertThat(mappedApplication.getOffences()).extracting("code").containsExactly("LINKOFF");
    }
}
