package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPCaseMarkerRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private CPCaseHearingRepository pcrCaseHearingRepository;

    @Autowired
    private CPCaseMarkerRepository pcrCaseMarkerRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        pcrCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        final CPCaseMarkerEntity entity = CPCaseMarkerEntity.builder()
                .id(MARKER_ID)
                .caseHearingId(CASE_HEARING_ID)
                .code("DV")
                .description("Domestic violence case marker")
                .build();

        pcrCaseMarkerRepository.save(entity);
        flushAndClear();

        final Optional<CPCaseMarkerEntity> found = pcrCaseMarkerRepository.findById(MARKER_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(found.get().getCode()).isEqualTo("DV");
        assertThat(found.get().getDescription()).isEqualTo("Domestic violence case marker");
    }
}
