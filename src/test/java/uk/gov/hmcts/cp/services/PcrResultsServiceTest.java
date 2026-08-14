package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.mappers.PcrResultsMapper;
import uk.gov.hmcts.cp.openapi.model.PcrHearingResult;
import uk.gov.hmcts.cp.repositories.CPCaseHearingRepository;
import uk.gov.hmcts.cp.repositories.CPCaseMarkerRepository;
import uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultRepository;
import uk.gov.hmcts.cp.repositories.CPOffenceRepository;
import uk.gov.hmcts.cp.repositories.CPVersionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PcrResultsServiceTest {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000044");

    @Mock
    private CPCaseHearingRepository caseHearingRepository;
    @Mock
    private CPVersionRepository versionRepository;
    @Mock
    private CPCaseMarkerRepository caseMarkerRepository;
    @Mock
    private CPCourtApplicationRepository courtApplicationRepository;
    @Mock
    private CPOffenceRepository offenceRepository;
    @Mock
    private CPJudicialResultRepository judicialResultRepository;
    @Mock
    private CPJudicialResultPromptRepository judicialResultPromptRepository;
    @Mock
    private PcrResultsMapper mapper;

    @InjectMocks
    private PcrResultsService pcrResultsService;

    @Test
    void getPcrHearingResults_should_returnEmptyList_whenCaseHearingNotFound() {
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.empty());

        final List<PcrHearingResult> result = pcrResultsService.getPcrHearingResults(CASE_URN, HEARING_ID, DEFENDANT_ID);

        assertThat(result).isEmpty();
        verify(versionRepository, never()).findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void getPcrHearingResults_should_returnEmptyList_whenNoVersionsFound() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.of(caseHearing));
        when(versionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(CASE_HEARING_ID, DEFENDANT_ID))
                .thenReturn(List.of());

        final List<PcrHearingResult> result = pcrResultsService.getPcrHearingResults(CASE_URN, HEARING_ID, DEFENDANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getPcrHearingResults_should_gatherChildrenAndMapEachVersion() {
        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        final CPVersionEntity version = CPVersionEntity.builder().cpVersionPk(VERSION_PK).build();
        final PcrHearingResult mapped = PcrHearingResult.builder().build();
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.of(caseHearing));
        when(versionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(CASE_HEARING_ID, DEFENDANT_ID))
                .thenReturn(List.of(version));
        when(caseMarkerRepository.findByCaseHearingId(CASE_HEARING_ID)).thenReturn(List.of());
        when(courtApplicationRepository.findByVersionPk(VERSION_PK)).thenReturn(List.of());
        when(offenceRepository.findByVersionPk(VERSION_PK)).thenReturn(List.of());
        when(mapper.toPcrHearingResult(caseHearing, version, List.of(), List.of(), List.of(), List.of(), List.of()))
                .thenReturn(mapped);

        final List<PcrHearingResult> result = pcrResultsService.getPcrHearingResults(CASE_URN, HEARING_ID, DEFENDANT_ID);

        assertThat(result).containsExactly(mapped);
    }

    @Test
    void getPcrHearingResults_should_gatherOffencesAndJudicialResultsOnce_whenDirectAndLinkedOffencesExist() {
        final UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000000055");
        final UUID directOffenceId = UUID.fromString("00000000-0000-0000-0000-000000000066");
        final UUID linkedOffenceId = UUID.fromString("00000000-0000-0000-0000-000000000077");
        final UUID directResultId = UUID.fromString("00000000-0000-0000-0000-000000000088");
        final UUID linkedResultId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        final CPCaseHearingEntity caseHearing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        final CPVersionEntity version = CPVersionEntity.builder().cpVersionPk(VERSION_PK).build();
        final CPCourtApplicationEntity application = CPCourtApplicationEntity.builder().id(applicationId).versionPk(VERSION_PK).build();
        final CPOffenceEntity directOffence = CPOffenceEntity.builder().id(directOffenceId).versionPk(VERSION_PK).build();
        final CPOffenceEntity linkedOffence = CPOffenceEntity.builder().id(linkedOffenceId).courtApplicationId(applicationId).build();
        final CPJudicialResultEntity directResult = CPJudicialResultEntity.builder().id(directResultId).offenceId(directOffenceId).build();
        final CPJudicialResultEntity linkedResult = CPJudicialResultEntity.builder().id(linkedResultId).offenceId(linkedOffenceId).build();

        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.of(caseHearing));
        when(versionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(CASE_HEARING_ID, DEFENDANT_ID))
                .thenReturn(List.of(version));
        when(caseMarkerRepository.findByCaseHearingId(CASE_HEARING_ID)).thenReturn(List.of());
        when(courtApplicationRepository.findByVersionPk(VERSION_PK)).thenReturn(List.of(application));
        when(offenceRepository.findByVersionPk(VERSION_PK)).thenReturn(List.of(directOffence));
        when(offenceRepository.findByCourtApplicationId(applicationId)).thenReturn(List.of(linkedOffence));
        when(judicialResultRepository.findByOffenceId(directOffenceId)).thenReturn(List.of(directResult));
        when(judicialResultRepository.findByOffenceId(linkedOffenceId)).thenReturn(List.of(linkedResult));
        when(judicialResultRepository.findByCourtApplicationId(applicationId)).thenReturn(List.of());
        when(judicialResultPromptRepository.findByJudicialResultId(directResultId)).thenReturn(List.of());
        when(judicialResultPromptRepository.findByJudicialResultId(linkedResultId)).thenReturn(List.of());

        pcrResultsService.getPcrHearingResults(CASE_URN, HEARING_ID, DEFENDANT_ID);

        verify(judicialResultRepository, times(1)).findByOffenceId(directOffenceId);
        verify(judicialResultRepository, times(1)).findByOffenceId(linkedOffenceId);
        verify(judicialResultRepository, times(1)).findByCourtApplicationId(applicationId);
        verify(judicialResultPromptRepository, times(1)).findByJudicialResultId(directResultId);
        verify(judicialResultPromptRepository, times(1)).findByJudicialResultId(linkedResultId);
        verify(caseMarkerRepository, times(1)).findByCaseHearingId(CASE_HEARING_ID);
    }
}
