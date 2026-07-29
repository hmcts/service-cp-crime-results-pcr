package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPCaseMarkerRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPCaseMarkerRepository cpCaseMarkerRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
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

        cpCaseMarkerRepository.save(entity);

        final Optional<CPCaseMarkerEntity> found = cpCaseMarkerRepository.findById(MARKER_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(found.get().getCode()).isEqualTo("DV");
        assertThat(found.get().getDescription()).isEqualTo("Domestic violence case marker");
    }

    @Transactional
    @Test
    void findByCaseHearingId_should_returnMatchingMarkers() {
        final UUID caseHearingId = UUID.fromString("00000000-0000-0000-0000-000000000075");
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(caseHearingId)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        final CPCaseMarkerEntity marker = CPCaseMarkerEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000076"))
                .caseHearingId(caseHearingId).code("DomesticViolence").build();
        cpCaseMarkerRepository.save(marker);

        final List<CPCaseMarkerEntity> found = cpCaseMarkerRepository.findByCaseHearingId(caseHearingId);

        assertThat(found).extracting(CPCaseMarkerEntity::getCode).containsExactly("DomesticViolence");
    }
}
