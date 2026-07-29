package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPOffenceRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID SOURCE_OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPVersionRepository cpVersionRepository;

    @Autowired
    private CPCourtApplicationRepository cpCourtApplicationRepository;

    @Autowired
    private CPOffenceRepository cpOffenceRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenParentedByVersion() {
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

        final CPOffenceEntity entity = CPOffenceEntity.builder()
                .id(OFFENCE_ID)
                .versionPk(VERSION_PK)
                .sourceOffenceId(SOURCE_OFFENCE_ID)
                .code("TH68001")
                .title("Theft from a shop")
                .wording("On 1 July 2026 stole goods")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 1))
                .listingNumber(1)
                .convictionDate(LocalDate.of(2026, 7, 23))
                .pleaValue("GUILTY")
                .pleaDate(LocalDate.of(2026, 7, 23))
                .verdictCode("G")
                .build();

        cpOffenceRepository.save(entity);

        final Optional<CPOffenceEntity> found = cpOffenceRepository.findById(OFFENCE_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getVersionPk()).isEqualTo(VERSION_PK);
        assertThat(found.get().getCourtApplicationId()).isNull();
        assertThat(found.get().getSourceOffenceId()).isEqualTo(SOURCE_OFFENCE_ID);
        assertThat(found.get().getCode()).isEqualTo("TH68001");
        assertThat(found.get().getTitle()).isEqualTo("Theft from a shop");
        assertThat(found.get().getWording()).isEqualTo("On 1 July 2026 stole goods");
        assertThat(found.get().getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(found.get().getEndDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(found.get().getListingNumber()).isEqualTo(1);
        assertThat(found.get().getConvictionDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(found.get().getPleaValue()).isEqualTo("GUILTY");
        assertThat(found.get().getPleaDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(found.get().getVerdictCode()).isEqualTo("G");
    }

    @Transactional
    @Test
    void findByVersionPk_should_returnDirectOffences() {
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        final UUID versionPk = UUID.fromString("00000000-0000-0000-0000-000000000080");
        cpVersionRepository.save(CPVersionEntity.builder()
                .cpVersionPk(versionPk)
                .defendantId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
                .caseHearingId(CASE_HEARING_ID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
                .build());
        final CPOffenceEntity offence = CPOffenceEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000081"))
                .versionPk(versionPk).code("TH68001").build();
        cpOffenceRepository.save(offence);

        final List<CPOffenceEntity> found = cpOffenceRepository.findByVersionPk(versionPk);

        assertThat(found).extracting(CPOffenceEntity::getCode).containsExactly("TH68001");
    }

    @Transactional
    @Test
    void findByCourtApplicationId_should_returnLinkedOffences() {
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
        final UUID courtApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000082");
        cpCourtApplicationRepository.save(CPCourtApplicationEntity.builder()
                .id(courtApplicationId)
                .versionPk(VERSION_PK)
                .reference("APP-1")
                .build());
        final CPOffenceEntity offence = CPOffenceEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000083"))
                .courtApplicationId(courtApplicationId).code("TH68002").build();
        cpOffenceRepository.save(offence);

        final List<CPOffenceEntity> found = cpOffenceRepository.findByCourtApplicationId(courtApplicationId);

        assertThat(found).extracting(CPOffenceEntity::getCode).containsExactly("TH68002");
    }
}
