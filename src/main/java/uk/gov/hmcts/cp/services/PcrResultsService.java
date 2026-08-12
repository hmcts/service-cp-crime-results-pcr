package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
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
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PcrResultsService {

    private final CPCaseHearingRepository caseHearingRepository;
    private final CPVersionRepository versionRepository;
    private final CPCaseMarkerRepository caseMarkerRepository;
    private final CPCourtApplicationRepository courtApplicationRepository;
    private final CPOffenceRepository offenceRepository;
    private final CPJudicialResultRepository judicialResultRepository;
    private final CPJudicialResultPromptRepository judicialResultPromptRepository;
    private final PcrResultsMapper mapper;

    @Transactional(readOnly = true)
    public List<PcrHearingResult> getPcrHearingResults(final String caseURN, final UUID hearingId, final UUID defendantId) {
        return caseHearingRepository.findByCaseUrnAndHearingId(caseURN, hearingId)
                .map(caseHearing -> toResults(caseHearing, defendantId))
                .orElseGet(List::of);
    }

    private List<PcrHearingResult> toResults(final CPCaseHearingEntity caseHearing, final UUID defendantId) {
        final List<CPCaseMarkerEntity> caseMarkers = caseMarkerRepository.findByCaseHearingId(caseHearing.getId());
        return versionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(caseHearing.getId(), defendantId).stream()
                .map(version -> toPcrHearingResult(caseHearing, version, caseMarkers))
                .toList();
    }

    private PcrHearingResult toPcrHearingResult(final CPCaseHearingEntity caseHearing, final CPVersionEntity version,
                                                 final List<CPCaseMarkerEntity> caseMarkers) {
        final List<CPCourtApplicationEntity> courtApplications = courtApplicationRepository.findByVersionPk(version.getCpVersionPk());
        final List<CPOffenceEntity> offences = allOffences(version.getCpVersionPk(), courtApplications);
        final List<CPJudicialResultEntity> judicialResults = allJudicialResults(version.getCpVersionPk(), offences, courtApplications);
        return mapper.toPcrHearingResult(
                caseHearing,
                version,
                caseMarkers,
                courtApplications,
                offences,
                judicialResults,
                allPrompts(judicialResults));
    }

    private List<CPOffenceEntity> allOffences(final UUID versionPk, final List<CPCourtApplicationEntity> courtApplications) {
        final Stream<CPOffenceEntity> direct = offenceRepository.findByVersionPk(versionPk).stream();
        final Stream<CPOffenceEntity> linked = courtApplications.stream()
                .flatMap(a -> offenceRepository.findByCourtApplicationId(a.getId()).stream());
        return Stream.concat(direct, linked).toList();
    }

    private List<CPJudicialResultEntity> allJudicialResults(final UUID versionPk,
            final List<CPOffenceEntity> offences, final List<CPCourtApplicationEntity> courtApplications) {
        final Stream<CPJudicialResultEntity> offenceResults = offences.stream()
                .flatMap(o -> judicialResultRepository.findByOffenceId(o.getId()).stream());
        final Stream<CPJudicialResultEntity> applicationResults = courtApplications.stream()
                .flatMap(a -> judicialResultRepository.findByCourtApplicationId(a.getId()).stream());
        // Third parent (design doc §3 extension) — defendantResults/caseResults, distinguished
        // from each other by level, not by a separate repository lookup.
        final Stream<CPJudicialResultEntity> versionResults = judicialResultRepository.findByVersionPk(versionPk).stream();
        return Stream.concat(Stream.concat(offenceResults, applicationResults), versionResults).toList();
    }

    private List<CPJudicialResultPromptEntity> allPrompts(final List<CPJudicialResultEntity> judicialResults) {
        return judicialResults.stream()
                .flatMap(r -> judicialResultPromptRepository.findByJudicialResultId(r.getId()).stream())
                .toList();
    }
}
