package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.PcrCaseHearingEntity;
import uk.gov.hmcts.cp.entities.PcrCaseMarkerEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PcrCaseMarkerRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private PcrCaseHearingRepository pcrCaseHearingRepository;

    @Autowired
    private PcrCaseMarkerRepository pcrCaseMarkerRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        pcrCaseHearingRepository.save(PcrCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        final PcrCaseMarkerEntity entity = PcrCaseMarkerEntity.builder()
                .id(MARKER_ID)
                .caseHearingId(CASE_HEARING_ID)
                .code("DV")
                .description("Domestic violence case marker")
                .build();

        pcrCaseMarkerRepository.save(entity);
        flushAndClear();

        final Optional<PcrCaseMarkerEntity> found = pcrCaseMarkerRepository.findById(MARKER_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(found.get().getCode()).isEqualTo("DV");
        assertThat(found.get().getDescription()).isEqualTo("Domestic violence case marker");
    }
}
