package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPNextHearingEmbeddable;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPVersionRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MASTER_DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID NEXT_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPVersionRepository cpVersionRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        final OffsetDateTime expiresAt = createdAt.plusDays(30);
        final CPVersionEntity entity = CPVersionEntity.builder()
                .cpVersionPk(VERSION_PK)
                .sourceId("SRC-1")
                .defendantId(DEFENDANT_ID)
                .caseHearingId(CASE_HEARING_ID)
                .custodyLocation("Prison")
                .custodyType("HMP Dovegate")
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .nextHearing(CPNextHearingEmbeddable.builder()
                        .date(LocalDate.of(2026, 8, 1))
                        .time("10:00")
                        .courtHouseCode("B01LY")
                        .courtHouseName("Leeds Crown Court")
                        .id(NEXT_HEARING_ID)
                        .build())
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .title("Mr")
                .firstName("encrypted-first-name")
                .middleName("encrypted-middle-name")
                .lastName("encrypted-last-name")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .addressLine1("encrypted-address-1")
                .addressLine2("encrypted-address-2")
                .addressLine3("encrypted-address-3")
                .addressLine4("encrypted-address-4")
                .addressLine5("encrypted-address-5")
                .postCode("encrypted-postcode")
                .build();

        cpVersionRepository.save(entity);

        final Optional<CPVersionEntity> found = cpVersionRepository.findById(VERSION_PK);
        assertThat(found).isPresent();
        final CPVersionEntity actual = found.get();
        assertThat(actual.getSourceId()).isEqualTo("SRC-1");
        assertThat(actual.getDefendantId()).isEqualTo(DEFENDANT_ID);
        assertThat(actual.getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(actual.getCustodyLocation()).isEqualTo("Prison");
        assertThat(actual.getCustodyType()).isEqualTo("HMP Dovegate");
        assertThat(actual.getMasterDefendantId()).isEqualTo(MASTER_DEFENDANT_ID);
        assertThat(actual.getNextHearing().getDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(actual.getNextHearing().getTime()).isEqualTo("10:00");
        assertThat(actual.getNextHearing().getCourtHouseCode()).isEqualTo("B01LY");
        assertThat(actual.getNextHearing().getCourtHouseName()).isEqualTo("Leeds Crown Court");
        assertThat(actual.getNextHearing().getId()).isEqualTo(NEXT_HEARING_ID);
        assertThat(actual.getCreatedAt()).isEqualTo(createdAt);
        assertThat(actual.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(actual.getTitle()).isEqualTo("Mr");
        assertThat(actual.getFirstName()).isEqualTo("encrypted-first-name");
        assertThat(actual.getMiddleName()).isEqualTo("encrypted-middle-name");
        assertThat(actual.getLastName()).isEqualTo("encrypted-last-name");
        assertThat(actual.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(actual.getAddressLine1()).isEqualTo("encrypted-address-1");
        assertThat(actual.getAddressLine2()).isEqualTo("encrypted-address-2");
        assertThat(actual.getAddressLine3()).isEqualTo("encrypted-address-3");
        assertThat(actual.getAddressLine4()).isEqualTo("encrypted-address-4");
        assertThat(actual.getAddressLine5()).isEqualTo("encrypted-address-5");
        assertThat(actual.getPostCode()).isEqualTo("encrypted-postcode");
    }

    @Transactional
    @Test
    void findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc_should_returnVersionsOldestFirst() {
        final UUID caseHearingId = UUID.fromString("00000000-0000-0000-0000-000000000077");
        cpCaseHearingRepository.save(CPCaseHearingEntity.builder()
                .id(caseHearingId)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        final UUID defendantId = UUID.fromString("00000000-0000-0000-0000-000000000088");
        final OffsetDateTime older = OffsetDateTime.parse("2026-07-01T10:00:00Z");
        final OffsetDateTime newer = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        final CPVersionEntity newerVersion = CPVersionEntity.builder()
                .cpVersionPk(UUID.fromString("00000000-0000-0000-0000-000000000091"))
                .caseHearingId(caseHearingId).defendantId(defendantId).createdAt(newer)
                .expiresAt(newer.plusDays(30)).build();
        final CPVersionEntity olderVersion = CPVersionEntity.builder()
                .cpVersionPk(UUID.fromString("00000000-0000-0000-0000-000000000092"))
                .caseHearingId(caseHearingId).defendantId(defendantId).createdAt(older)
                .expiresAt(older.plusDays(30)).build();
        cpVersionRepository.save(newerVersion);
        cpVersionRepository.save(olderVersion);

        final List<CPVersionEntity> found =
                cpVersionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(caseHearingId, defendantId);

        assertThat(found).extracting(CPVersionEntity::getCpVersionPk)
                .containsExactly(olderVersion.getCpVersionPk(), newerVersion.getCpVersionPk());
    }

    @Transactional
    @Test
    void findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc_should_returnEmpty_whenNoMatch() {
        final List<CPVersionEntity> found = cpVersionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(
                UUID.fromString("00000000-0000-0000-0000-000000000093"), UUID.fromString("00000000-0000-0000-0000-000000000094"));

        assertThat(found).isEmpty();
    }
}
