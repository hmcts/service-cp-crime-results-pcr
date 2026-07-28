package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.mappers.CPVersionEntityMapper;
import uk.gov.hmcts.cp.mappers.CPVersionWriteBundle;
import uk.gov.hmcts.cp.repositories.CPCaseHearingRepository;
import uk.gov.hmcts.cp.repositories.CPCaseMarkerRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultRepository;
import uk.gov.hmcts.cp.repositories.CPOffenceRepository;
import uk.gov.hmcts.cp.repositories.CPVersionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResultsPcrControllerIntegrationTest extends ControllerRepositoryIntegrationTestBase {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final UUID UNKNOWN_DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String UNKNOWN_CASE_URN = "ZZZZ9999999";

    @Autowired
    private CPVersionEntityMapper mapper;
    @Autowired
    private CPCaseHearingRepository caseHearingRepository;
    @Autowired
    private CPCaseMarkerRepository caseMarkerRepository;
    @Autowired
    private CPVersionRepository versionRepository;
    @Autowired
    private CPOffenceRepository offenceRepository;
    @Autowired
    private CPJudicialResultRepository judicialResultRepository;
    @Autowired
    private CPJudicialResultPromptRepository judicialResultPromptRepository;

    @Transactional
    @Test
    void getPcrHearingResults_should_returnOk_withMappedFields_whenRecorded() throws Exception {
        seedOneVersion();

        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}", CASE_URN, HEARING_ID, DEFENDANT_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseURN").value(CASE_URN))
                .andExpect(jsonPath("$[0].defendant.masterDefendantId").value(MASTER_DEFENDANT_ID))
                .andExpect(jsonPath("$[0].caseMarkers[0].code").value("DomesticViolence"))
                .andExpect(jsonPath("$[0].offences[0].code").value("TH68001"))
                .andExpect(jsonPath("$[0].offences[0].judicialResults[0].resultCode").value("1200"))
                .andExpect(jsonPath("$[0].offences[0].judicialResults[0].convicted").value(true))
                .andExpect(jsonPath("$[0].offences[0].judicialResults[0].financial").value(false));
    }

    @Transactional
    @Test
    void getPcrHearingResults_should_returnEmptyList_whenDefendantNeverRecorded() throws Exception {
        seedOneVersion();

        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}", CASE_URN, HEARING_ID, UNKNOWN_DEFENDANT_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Transactional
    @Test
    void getPcrHearingResults_should_returnEmptyList_whenCaseUrnUnknown() throws Exception {
        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}", UNKNOWN_CASE_URN, HEARING_ID, DEFENDANT_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getPcrHearingResults_should_return400_whenCaseUrnInvalid() throws Exception {
        mockMvc.perform(get("/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}", "bad urn!", HEARING_ID, DEFENDANT_ID))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    private void seedOneVersion() {
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of(CaseMarker.builder().markerTypeCode("DomesticViolence").build()))
                .defendants(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .prosecutionCases(List.of(prosecutionCase))
                .courtApplications(List.of())
                .build();
        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        final var caseHearing = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, createdAt);
        caseHearingRepository.save(caseHearing);
        caseMarkerRepository.saveAll(mapper.toCaseMarkerEntities(prosecutionCase, caseHearing.getId()));

        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offenceWithResult()))
                .build();
        final CPVersionWriteBundle bundle = mapper.toWriteBundle(defendant, hearing, caseHearing.getId(), createdAt, createdAt.plusDays(30));
        versionRepository.save(bundle.version());
        offenceRepository.saveAll(bundle.offences());
        judicialResultRepository.saveAll(bundle.judicialResults());
        judicialResultPromptRepository.saveAll(bundle.judicialResultPrompts());
    }

    private Offence offenceWithResult() {
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").label("Sentenced")
                .isFinancialResult(false).isConvictedResult(true)
                .judicialResultPrompts(List.of())
                .build();
        return Offence.builder().offenceCode("TH68001").judicialResults(List.of(result)).build();
    }
}
