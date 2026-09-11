package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPRelatedCaseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPRelatedCaseRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final UUID LINKED_CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPRelatedCaseRepository cpRelatedCaseRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("IE137532124")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000083"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        final CPRelatedCaseEntity entity = CPRelatedCaseEntity.builder()
                .id(LINKED_CASE_ID)
                .caseHearingId(CASE_HEARING_ID)
                .caseUrn("XI137534386")
                .build();

        cpRelatedCaseRepository.save(entity);

        final Optional<CPRelatedCaseEntity> found = cpRelatedCaseRepository.findById(LINKED_CASE_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(found.get().getCaseUrn()).isEqualTo("XI137534386");
    }

    @Transactional
    @Test
    void findByCaseHearingId_should_returnMatchingRelatedCases() {
        final UUID caseHearingId = UUID.fromString("00000000-0000-0000-0000-000000000084");
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(caseHearingId)
                .caseUrn("IE137532124")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000085"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        cpRelatedCaseRepository.save(CPRelatedCaseEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000086"))
                .caseHearingId(caseHearingId).caseUrn("IE137532124").build());
        cpRelatedCaseRepository.save(CPRelatedCaseEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000087"))
                .caseHearingId(caseHearingId).caseUrn("XI137534386").build());

        final List<CPRelatedCaseEntity> found = cpRelatedCaseRepository.findByCaseHearingId(caseHearingId);

        assertThat(found).extracting(CPRelatedCaseEntity::getCaseUrn).containsExactlyInAnyOrder("IE137532124", "XI137534386");
    }
}
