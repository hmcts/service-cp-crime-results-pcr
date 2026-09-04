package uk.gov.hmcts.cp.services.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
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

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CPEntityPersistenceServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String CASE_URN = "ABCD1234567";
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000044");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 7, 28, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime EXPIRES_AT = CREATED_AT.plusDays(30);
    private static final Instant SHARED_TIME = Instant.parse("2026-07-28T09:33:21Z");

    @Mock
    private CPHearingResultEntityMapper entityMapper;
    @Spy
    private ClockService clockService = new ClockService(Clock.fixed(CREATED_AT.toInstant(), ZoneOffset.UTC));
    @Mock
    private CPCaseHearingRepository caseHearingRepository;
    @Mock
    private CPCaseMarkerRepository caseMarkerRepository;
    @Mock
    private CPVersionRepository versionRepository;
    @Mock
    private CPCourtApplicationRepository courtApplicationRepository;
    @Mock
    private CPOffenceRepository offenceRepository;
    @Mock
    private CPJudicialResultRepository judicialResultRepository;
    @Mock
    private CPJudicialResultPromptRepository judicialResultPromptRepository;

    @InjectMocks
    private CPEntityPersistenceService persistenceService;

    @Test
    void persist_should_saveEveryPartOfTheEntitySet_whenMapped() {
        final Defendant defendant = Defendant.builder().id("11111111-1111-1111-1111-111111111111").build();
        final HearingDetail hearing = HearingDetail.builder().build();
        final CPVersionEntity version = CPVersionEntity.builder().cpVersionPk(UUID.randomUUID()).build();
        final List<CPCourtApplicationEntity> courtApplications = List.of(CPCourtApplicationEntity.builder().build());
        final List<CPOffenceEntity> offences = List.of(CPOffenceEntity.builder().build());
        final List<CPJudicialResultEntity> judicialResults = List.of(CPJudicialResultEntity.builder().build());
        final List<CPJudicialResultPromptEntity> prompts = List.of(CPJudicialResultPromptEntity.builder().build());
        final CPEntitySet entitySet = new CPEntitySet(version, courtApplications, offences, judicialResults, prompts);
        when(entityMapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT)).thenReturn(entitySet);

        persistenceService.persist(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT);

        verify(versionRepository).save(version);
        verify(courtApplicationRepository).saveAll(courtApplications);
        verify(offenceRepository).saveAll(offences);
        verify(judicialResultRepository).saveAll(judicialResults);
        verify(judicialResultPromptRepository).saveAll(prompts);
    }

    @Test
    void findOrCreateCaseHearing_should_reuseExisting_whenAlreadyFound() {
        final ProsecutionCase prosecutionCase = prosecutionCase();
        final HearingDetail hearing = hearing(prosecutionCase);
        final CPCaseHearingEntity existing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.of(existing));

        final UUID result = persistenceService.findOrCreateCaseHearing(prosecutionCase, hearing, HEARING_ID);

        assertThat(result).isEqualTo(CASE_HEARING_ID);
        verify(caseHearingRepository, never()).save(any());
        verify(caseMarkerRepository, never()).saveAll(any());
    }

    @Test
    void findOrCreateCaseHearing_should_createNew_whenNotFound() {
        final ProsecutionCase prosecutionCase = prosecutionCase();
        final HearingDetail hearing = hearing(prosecutionCase);
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.empty());
        final CPCaseHearingEntity created = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(entityMapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT)).thenReturn(created);
        when(entityMapper.toCaseMarkerEntities(prosecutionCase, CASE_HEARING_ID)).thenReturn(List.of());

        final UUID result = persistenceService.findOrCreateCaseHearing(prosecutionCase, hearing, HEARING_ID);

        assertThat(result).isEqualTo(CASE_HEARING_ID);
        verify(caseHearingRepository).save(created);
        verify(caseMarkerRepository).saveAll(eq(List.of()));
    }

    @Test
    void persist_should_useDefendantTypeOverload_whenGiven() {
        final Defendant defendant = Defendant.builder().id("11111111-1111-1111-1111-111111111111").build();
        final HearingDetail hearing = HearingDetail.builder().build();
        final CPVersionEntity version = CPVersionEntity.builder().cpVersionPk(UUID.randomUUID()).build();
        final CPEntitySet entitySet = new CPEntitySet(version, List.of(), List.of(), List.of(), List.of());
        when(entityMapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT, "Respondent"))
                .thenReturn(entitySet);

        persistenceService.persist(defendant, hearing, CASE_HEARING_ID, SHARED_TIME, CREATED_AT, EXPIRES_AT, "Respondent");

        verify(versionRepository).save(version);
    }

    @Test
    void findOrCreateCaseHearing_should_reuseExisting_whenGivenPlainCaseUrn() {
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();
        final CPCaseHearingEntity existing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(caseHearingRepository.findByCaseUrnAndHearingId("APP-REF-1", HEARING_ID)).thenReturn(Optional.of(existing));

        final UUID result = persistenceService.findOrCreateCaseHearing("APP-REF-1", hearing, HEARING_ID, "City of London Police");

        assertThat(result).isEqualTo(CASE_HEARING_ID);
        verify(caseHearingRepository, never()).save(any());
        verify(caseMarkerRepository, never()).saveAll(any());
    }

    @Test
    void findOrCreateCaseHearing_should_createNew_andSkipCaseMarkers_whenGivenPlainCaseUrn() {
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();
        when(caseHearingRepository.findByCaseUrnAndHearingId("APP-REF-1", HEARING_ID)).thenReturn(Optional.empty());
        final CPCaseHearingEntity created = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(entityMapper.toCaseHearingEntity("APP-REF-1", hearing, HEARING_ID, CREATED_AT, "City of London Police", null)).thenReturn(created);

        final UUID result = persistenceService.findOrCreateCaseHearing("APP-REF-1", hearing, HEARING_ID, "City of London Police");

        assertThat(result).isEqualTo(CASE_HEARING_ID);
        verify(caseHearingRepository).save(created);
        verify(caseMarkerRepository, never()).saveAll(any());
    }

    private ProsecutionCase prosecutionCase() {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of())
                .build();
    }

    private HearingDetail hearing(final ProsecutionCase prosecutionCase) {
        return HearingDetail.builder()
                .courtCentre(CourtCentre.builder().build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .prosecutionCases(List.of(prosecutionCase))
                .courtApplications(List.of())
                .build();
    }
}