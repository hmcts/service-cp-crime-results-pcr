package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.mappers.PcrHearingResultMapper;
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
public class ResultsPcrService {

    private final CPCaseHearingRepository caseHearingRepository;
    private final CPVersionRepository versionRepository;
    private final CPCaseMarkerRepository caseMarkerRepository;
    private final CPCourtApplicationRepository courtApplicationRepository;
    private final CPOffenceRepository offenceRepository;
    private final CPJudicialResultRepository judicialResultRepository;
    private final CPJudicialResultPromptRepository judicialResultPromptRepository;
    private final PcrHearingResultMapper mapper;

    public List<PcrHearingResult> getPcrHearingResults(final String caseURN, final UUID hearingId, final UUID defendantId) {
        return caseHearingRepository.findByCaseUrnAndHearingId(caseURN, hearingId)
                .map(caseHearing -> toResults(caseHearing, defendantId))
                .orElseGet(List::of);
    }

    private List<PcrHearingResult> toResults(final CPCaseHearingEntity caseHearing, final UUID defendantId) {
        return versionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(caseHearing.getId(), defendantId).stream()
                .map(version -> toPcrHearingResult(caseHearing, version))
                .toList();
    }

    private PcrHearingResult toPcrHearingResult(final CPCaseHearingEntity caseHearing, final CPVersionEntity version) {
        final List<CPCourtApplicationEntity> courtApplications = courtApplicationRepository.findByVersionPk(version.getCpVersionPk());
        final List<CPOffenceEntity> offences = allOffences(version.getCpVersionPk(), courtApplications);
        return mapper.toPcrHearingResult(
                caseHearing,
                version,
                caseMarkerRepository.findByCaseHearingId(caseHearing.getId()),
                courtApplications,
                offences,
                allJudicialResults(offences, courtApplications),
                allPrompts(offences, courtApplications));
    }

    private List<CPOffenceEntity> allOffences(final UUID versionPk, final List<CPCourtApplicationEntity> courtApplications) {
        final Stream<CPOffenceEntity> direct = offenceRepository.findByVersionPk(versionPk).stream();
        final Stream<CPOffenceEntity> linked = courtApplications.stream()
                .flatMap(a -> offenceRepository.findByCourtApplicationId(a.getId()).stream());
        return Stream.concat(direct, linked).toList();
    }

    private List<CPJudicialResultEntity> allJudicialResults(
            final List<CPOffenceEntity> offences, final List<CPCourtApplicationEntity> courtApplications) {
        final Stream<CPJudicialResultEntity> offenceResults = offences.stream()
                .flatMap(o -> judicialResultRepository.findByOffenceId(o.getId()).stream());
        final Stream<CPJudicialResultEntity> applicationResults = courtApplications.stream()
                .flatMap(a -> judicialResultRepository.findByCourtApplicationId(a.getId()).stream());
        return Stream.concat(offenceResults, applicationResults).toList();
    }

    private List<CPJudicialResultPromptEntity> allPrompts(
            final List<CPOffenceEntity> offences, final List<CPCourtApplicationEntity> courtApplications) {
        return allJudicialResults(offences, courtApplications).stream()
                .flatMap(r -> judicialResultPromptRepository.findByJudicialResultId(r.getId()).stream())
                .toList();
    }
}
