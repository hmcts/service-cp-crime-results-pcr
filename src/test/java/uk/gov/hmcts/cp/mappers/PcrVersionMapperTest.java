package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDetails;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Respondent;
import uk.gov.hmcts.cp.openapi.model.PcrVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PcrVersionMapperTest {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";

    @Mock
    private JudicialResultPromptParser promptParser;

    @InjectMocks
    private PcrVersionMapper mapper;

    @Test
    void toPcrVersion_should_leaveIdNull() {
        final PcrVersion result = mapper.toPcrVersion(minimalDefendant(), minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getId()).isNull();
    }

    @Test
    void toPcrVersion_should_mapProsecutionCaseAndCaseMarkers() {
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of(CaseMarker.builder().markerTypeCode("DomesticViolence").build()))
                .defendants(List.of())
                .build();

        final PcrVersion result = mapper.toPcrVersion(minimalDefendant(), prosecutionCase, minimalHearingDetails(), HEARING_ID);

        assertThat(result.getProsecutionCase().getCaseURN()).isEqualTo(CASE_URN);
        assertThat(result.getCaseMarkers()).extracting("code").containsExactly("DomesticViolence");
    }

    @Test
    void toDefendant_should_mapPersonDetailsAndAddress() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder()
                                .title("Mr").firstName("John").middleName("Middle").lastName("Doe")
                                .dateOfBirth(LocalDate.of(1980, 1, 31))
                                .address(Address.builder().address1("1 Example Street").postcode("AB1 2CD").build())
                                .build())
                        .build())
                .offences(List.of())
                .build();

        final PcrVersion result = mapper.toPcrVersion(defendant, minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getDefendant().getId()).isEqualTo(DEFENDANT_ID);
        assertThat(result.getDefendant().getFirstName()).isEqualTo("John");
        assertThat(result.getDefendant().getLastName()).isEqualTo("Doe");
        assertThat(result.getDefendant().getDateOfBirth()).isEqualTo(LocalDate.of(1980, 1, 31));
        assertThat(result.getDefendant().getAddress().getAddress1()).isEqualTo("1 Example Street");
        assertThat(result.getDefendant().getAddress().getPostCode()).isEqualTo("AB1 2CD");
    }

    @Test
    void toDefendant_should_mapMasterDefendantId() {
        final Defendant defendant = defendantWithMasterId(MASTER_DEFENDANT_ID);

        final PcrVersion result = mapper.toPcrVersion(defendant, minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getDefendant().getMasterDefendantId()).isEqualTo(UUID.fromString(MASTER_DEFENDANT_ID));
    }

    @Test
    void toCustodyLocation_should_mapCustodialEstablishmentName() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder().build())
                        .custodialEstablishment(CustodialEstablishment.builder().name("HMP Dovegate").custody("Prison").build())
                        .build())
                .offences(List.of())
                .build();

        final PcrVersion result = mapper.toPcrVersion(defendant, minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getCustodyLocation()).isEqualTo("HMP Dovegate");
    }

    @Test
    void toCustodyLocation_should_returnNull_whenNoEstablishment() {
        final PcrVersion result = mapper.toPcrVersion(minimalDefendant(), minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getCustodyLocation()).isNull();
    }

    @Test
    void toHearingDetails_should_leaveOpenFieldsNull() {
        final PcrVersion result = mapper.toPcrVersion(minimalDefendant(), minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getHearing().getCourtHouseName()).isNull();
        assertThat(result.getHearing().getHearingOutcome()).isNull();
        assertThat(result.getHearing().getWarrantType()).isNull();
        assertThat(result.getHearing().getOverallConvictionDate()).isNull();
    }

    @Test
    void findNextHearing_should_returnFirstNonNull_acrossOffencesAndResults() {
        final JudicialResult resultWithNextHearing = JudicialResult.builder()
                .nextHearing(HearingDetailsResponse.NextHearing.builder().date(LocalDate.of(2026, 7, 21)).build())
                .judicialResultPrompts(List.of())
                .build();
        final Offence offence = Offence.builder().judicialResults(List.of(resultWithNextHearing)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().personDetails(PersonDetails.builder().build()).build())
                .offences(List.of(offence))
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(defendant))
                .build();
        final HearingDetailsResponse hearingDetails = HearingDetailsResponse.builder()
                .hearing(HearingDetail.builder()
                        .hearingDays(List.of())
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of())
                        .build())
                .build();

        final PcrVersion result = mapper.toPcrVersion(defendant, prosecutionCase, hearingDetails, HEARING_ID);

        assertThat(result.getHearing().getNextHearing().getDate()).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    @Test
    void toOffence_should_mapListingNumber() {
        final Offence offence = Offence.builder()
                .offenceCode("TH68001")
                .listingNumber(1)
                .judicialResults(List.of())
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().personDetails(PersonDetails.builder().build()).build())
                .offences(List.of(offence))
                .build();

        final PcrVersion result = mapper.toPcrVersion(defendant, minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getOffences().get(0).getListingNumber()).isEqualTo(1);
    }

    @Test
    void toJudicialResult_should_mapFinancialAndConvictedAsYN() {
        final JudicialResult judicialResult = JudicialResult.builder()
                .cjsCode("1200").label("Imprisonment")
                .isFinancialResult(false).isConvictedResult(true)
                .judicialResultPrompts(List.of())
                .build();
        final Offence offence = Offence.builder().judicialResults(List.of(judicialResult)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().personDetails(PersonDetails.builder().build()).build())
                .offences(List.of(offence))
                .build();

        final PcrVersion result = mapper.toPcrVersion(defendant, minimalProsecutionCase(), minimalHearingDetails(), HEARING_ID);

        assertThat(result.getOffences().get(0).getResults().get(0).getFinancial().getValue()).isEqualTo("N");
        assertThat(result.getOffences().get(0).getResults().get(0).getConvicted().getValue()).isEqualTo("Y");
    }

    @Test
    void toCourtApplications_should_filterByMasterDefendantId() {
        final Respondent matchingRespondent = Respondent.builder().masterDefendantId(MASTER_DEFENDANT_ID).build();
        final Respondent otherRespondent = Respondent.builder().masterDefendantId("44444444-4444-4444-4444-444444444444").build();
        final CourtApplication matchingApplication = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .respondents(List.of(matchingRespondent))
                .courtApplicationCases(List.of())
                .judicialResults(List.of())
                .build();
        final CourtApplication otherApplication = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e60")
                .respondents(List.of(otherRespondent))
                .courtApplicationCases(List.of())
                .judicialResults(List.of())
                .build();
        final Defendant defendant = defendantWithMasterId(MASTER_DEFENDANT_ID);
        final HearingDetailsResponse hearingDetails = HearingDetailsResponse.builder()
                .hearing(HearingDetail.builder()
                        .hearingDays(List.of())
                        .prosecutionCases(List.of())
                        .courtApplications(List.of(matchingApplication, otherApplication))
                        .build())
                .build();

        final PcrVersion result = mapper.toPcrVersion(defendant, minimalProsecutionCase(), hearingDetails, HEARING_ID);

        assertThat(result.getCourtApplications()).extracting(a -> a.getId().toString())
                .containsExactly("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f");
    }

    private Defendant minimalDefendant() {
        return Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().personDetails(PersonDetails.builder().build()).build())
                .offences(List.of())
                .build();
    }

    private Defendant defendantWithMasterId(final String masterDefendantId) {
        return Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(masterDefendantId)
                .personDefendant(PersonDefendant.builder().personDetails(PersonDetails.builder().build()).build())
                .offences(List.of())
                .build();
    }

    private ProsecutionCase minimalProsecutionCase() {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of())
                .build();
    }

    private HearingDetailsResponse minimalHearingDetails() {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetail.builder()
                        .courtCentre(CourtCentre.builder().build())
                        .hearingDays(List.of())
                        .prosecutionCases(List.of())
                        .courtApplications(List.of())
                        .build())
                .build();
    }
}