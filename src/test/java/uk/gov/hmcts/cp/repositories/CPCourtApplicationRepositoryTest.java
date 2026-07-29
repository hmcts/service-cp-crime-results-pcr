package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPCourtApplicationRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID COURT_APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID SOURCE_APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPVersionRepository cpVersionRepository;

    @Autowired
    private CPCourtApplicationRepository cpCourtApplicationRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        saveParents();

        final CPCourtApplicationEntity entity = CPCourtApplicationEntity.builder()
                .id(COURT_APPLICATION_ID)
                .versionPk(VERSION_PK)
                .sourceApplicationId(SOURCE_APPLICATION_ID)
                .reference("APP-1")
                .type("Breach")
                .decision("Granted")
                .decisionDate(LocalDate.of(2026, 7, 20))
                .response("Contested")
                .responseDate(LocalDate.of(2026, 7, 21))
                .build();

        cpCourtApplicationRepository.save(entity);

        final Optional<CPCourtApplicationEntity> found = cpCourtApplicationRepository.findById(COURT_APPLICATION_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getVersionPk()).isEqualTo(VERSION_PK);
        assertThat(found.get().getSourceApplicationId()).isEqualTo(SOURCE_APPLICATION_ID);
        assertThat(found.get().getReference()).isEqualTo("APP-1");
        assertThat(found.get().getType()).isEqualTo("Breach");
        assertThat(found.get().getDecision()).isEqualTo("Granted");
        assertThat(found.get().getDecisionDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(found.get().getResponse()).isEqualTo("Contested");
        assertThat(found.get().getResponseDate()).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    @Transactional
    @Test
    void findByVersionPk_should_returnMatchingApplications() {
        saveParents();
        final UUID versionPk = UUID.fromString("00000000-0000-0000-0000-000000000078");
        final CPVersionEntity versionEntity = CPVersionEntity.builder()
                .cpVersionPk(versionPk)
                .defendantId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
                .caseHearingId(CASE_HEARING_ID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
                .build();
        cpVersionRepository.save(versionEntity);
        final CPCourtApplicationEntity application = CPCourtApplicationEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000079"))
                .versionPk(versionPk).reference("REF1").build();
        cpCourtApplicationRepository.save(application);

        final List<CPCourtApplicationEntity> found = cpCourtApplicationRepository.findByVersionPk(versionPk);

        assertThat(found).extracting(CPCourtApplicationEntity::getReference).containsExactly("REF1");
    }

    private void saveParents() {
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        cpVersionRepository.save(CPVersionEntity.builder()
                .cpVersionPk(VERSION_PK)
                .defendantId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
                .caseHearingId(CASE_HEARING_ID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
                .build());
    }
}
