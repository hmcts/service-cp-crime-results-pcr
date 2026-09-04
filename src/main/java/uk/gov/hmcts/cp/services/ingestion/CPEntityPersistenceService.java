package uk.gov.hmcts.cp.services.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.mappers.CPEntitySet;
import uk.gov.hmcts.cp.mappers.CPHearingResultEntityMapper;
import uk.gov.hmcts.cp.repositories.CPCaseHearingRepository;
import uk.gov.hmcts.cp.repositories.CPCaseMarkerRepository;
import uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultRepository;
import uk.gov.hmcts.cp.repositories.CPOffenceRepository;
import uk.gov.hmcts.cp.repositories.CPVersionRepository;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CPEntityPersistenceService {

    private final CPHearingResultEntityMapper entityMapper;
    private final ClockService clockService;
    private final CPCaseHearingRepository caseHearingRepository;
    private final CPCaseMarkerRepository caseMarkerRepository;
    private final CPVersionRepository versionRepository;
    private final CPCourtApplicationRepository courtApplicationRepository;
    private final CPOffenceRepository offenceRepository;
    private final CPJudicialResultRepository judicialResultRepository;
    private final CPJudicialResultPromptRepository judicialResultPromptRepository;

    public UUID findOrCreateCaseHearing(final ProsecutionCase prosecutionCase, final HearingDetail hearing, final UUID hearingId) {
        final String caseUrn = prosecutionCase.getProsecutionCaseIdentifier().getCaseURN();
        return caseHearingRepository.findByCaseUrnAndHearingId(caseUrn, hearingId)
                .map(CPCaseHearingEntity::getId)
                .orElseGet(() -> createCaseHearing(prosecutionCase, hearing, hearingId));
    }

    // Overload for a court-application-only case — no case markers, CP has none for these.
    public UUID findOrCreateCaseHearing(final String caseUrn, final HearingDetail hearing, final UUID hearingId,
                                         final String prosecutorName) {
        return caseHearingRepository.findByCaseUrnAndHearingId(caseUrn, hearingId)
                .map(CPCaseHearingEntity::getId)
                .orElseGet(() -> createCaseHearing(caseUrn, hearing, hearingId, prosecutorName));
    }

    private UUID createCaseHearing(final ProsecutionCase prosecutionCase, final HearingDetail hearing, final UUID hearingId) {
        final CPCaseHearingEntity entity = entityMapper.toCaseHearingEntity(prosecutionCase, hearing, hearingId, clockService.nowOffsetUTC());
        caseHearingRepository.save(entity);
        caseMarkerRepository.saveAll(entityMapper.toCaseMarkerEntities(prosecutionCase, entity.getId()));
        return entity.getId();
    }

    private UUID createCaseHearing(final String caseUrn, final HearingDetail hearing, final UUID hearingId,
                                    final String prosecutorName) {
        final CPCaseHearingEntity entity = entityMapper.toCaseHearingEntity(caseUrn, hearing, hearingId,
                clockService.nowOffsetUTC(), prosecutorName);
        caseHearingRepository.save(entity);
        return entity.getId();
    }

    public void persist(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                         final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        persistEntitySet(entityMapper.toWriteBundle(defendant, hearing, caseHearingId, sharedTime, createdAt, expiresAt));
    }

    // Overload for a court-application-only defendant's computed label.
    public void persist(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                         final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt,
                         final String defendantType) {
        persistEntitySet(entityMapper.toWriteBundle(defendant, hearing, caseHearingId, sharedTime, createdAt, expiresAt, defendantType));
    }

    private void persistEntitySet(final CPEntitySet entitySet) {
        versionRepository.save(entitySet.version());
        courtApplicationRepository.saveAll(entitySet.courtApplications());
        offenceRepository.saveAll(entitySet.offences());
        judicialResultRepository.saveAll(entitySet.judicialResults());
        judicialResultPromptRepository.saveAll(entitySet.judicialResultPrompts());
    }
}