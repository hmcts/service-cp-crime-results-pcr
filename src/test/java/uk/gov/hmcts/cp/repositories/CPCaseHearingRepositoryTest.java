package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPCaseHearingRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        final CPCaseHearingEntity entity = CPCaseHearingEntity.builder()
                .id(ID)
                .caseUrn("ABCD1234567")
                .hearingId(HEARING_ID)
                .courtHouseCode("B01LY")
                .courtHouseName("Leeds Crown Court")
                .hearingDate(LocalDate.of(2026, 7, 23))
                .hearingOutcome("Adjourned")
                .createdAt(createdAt)
                .build();

        cpCaseHearingRepository.save(entity);

        final Optional<CPCaseHearingEntity> found = cpCaseHearingRepository.findById(ID);
        assertThat(found).isPresent();
        assertThat(found.get().getCaseUrn()).isEqualTo("ABCD1234567");
        assertThat(found.get().getHearingId()).isEqualTo(HEARING_ID);
        assertThat(found.get().getCourtHouseCode()).isEqualTo("B01LY");
        assertThat(found.get().getCourtHouseName()).isEqualTo("Leeds Crown Court");
        assertThat(found.get().getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(found.get().getHearingOutcome()).isEqualTo("Adjourned");
        assertThat(found.get().getCreatedAt()).isEqualTo(createdAt);
    }

    @Transactional
    @Test
    void findByCaseUrnAndHearingId_should_returnEntity_whenMatchExists() {
        final CPCaseHearingEntity entity = CPCaseHearingEntity.builder()
                .id(ID)
                .caseUrn("ABCD1234567")
                .hearingId(HEARING_ID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).withNano(0))
                .build();
        cpCaseHearingRepository.save(entity);

        final Optional<CPCaseHearingEntity> found =
                cpCaseHearingRepository.findByCaseUrnAndHearingId("ABCD1234567", HEARING_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(ID);
    }

    @Transactional
    @Test
    void findByCaseUrnAndHearingId_should_returnEmpty_whenNoMatch() {
        final Optional<CPCaseHearingEntity> found =
                cpCaseHearingRepository.findByCaseUrnAndHearingId("NOMATCH1234", HEARING_ID);

        assertThat(found).isEmpty();
    }
}
