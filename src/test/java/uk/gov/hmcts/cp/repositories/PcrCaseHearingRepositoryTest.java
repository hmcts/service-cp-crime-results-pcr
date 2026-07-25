package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.PcrCaseHearingEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PcrCaseHearingRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private PcrCaseHearingRepository pcrCaseHearingRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        final PcrCaseHearingEntity entity = PcrCaseHearingEntity.builder()
                .id(ID)
                .caseUrn("ABCD1234567")
                .hearingId(HEARING_ID)
                .courtHouseCode("B01LY")
                .courtHouseName("Leeds Crown Court")
                .hearingDate(LocalDate.of(2026, 7, 23))
                .hearingOutcome("Adjourned")
                .createdAt(createdAt)
                .build();

        pcrCaseHearingRepository.save(entity);
        flushAndClear();

        final Optional<PcrCaseHearingEntity> found = pcrCaseHearingRepository.findById(ID);
        assertThat(found).isPresent();
        assertThat(found.get().getCaseUrn()).isEqualTo("ABCD1234567");
        assertThat(found.get().getHearingId()).isEqualTo(HEARING_ID);
        assertThat(found.get().getCourtHouseCode()).isEqualTo("B01LY");
        assertThat(found.get().getCourtHouseName()).isEqualTo("Leeds Crown Court");
        assertThat(found.get().getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(found.get().getHearingOutcome()).isEqualTo("Adjourned");
        assertThat(found.get().getCreatedAt()).isEqualTo(createdAt);
    }
}
