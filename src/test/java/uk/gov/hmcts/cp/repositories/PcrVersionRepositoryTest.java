package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.NextHearingEmbeddable;
import uk.gov.hmcts.cp.entities.PcrCaseHearingEntity;
import uk.gov.hmcts.cp.entities.PcrVersionEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PcrVersionRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MASTER_DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID NEXT_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Autowired
    private PcrCaseHearingRepository pcrCaseHearingRepository;

    @Autowired
    private PcrVersionRepository pcrVersionRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        pcrCaseHearingRepository.save(PcrCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        final OffsetDateTime expiresAt = createdAt.plusDays(30);
        final PcrVersionEntity entity = PcrVersionEntity.builder()
                .pcrVersionPk(VERSION_PK)
                .sourceId("SRC-1")
                .defendantId(DEFENDANT_ID)
                .caseHearingId(CASE_HEARING_ID)
                .custodyLocation("Prison")
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .nextHearing(NextHearingEmbeddable.builder()
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
                .dateOfBirth("encrypted-dob")
                .addressLine1("encrypted-address-1")
                .addressLine2("encrypted-address-2")
                .addressLine3("encrypted-address-3")
                .addressLine4("encrypted-address-4")
                .addressLine5("encrypted-address-5")
                .postCode("encrypted-postcode")
                .build();

        pcrVersionRepository.save(entity);
        flushAndClear();

        final Optional<PcrVersionEntity> found = pcrVersionRepository.findById(VERSION_PK);
        assertThat(found).isPresent();
        final PcrVersionEntity actual = found.get();
        assertThat(actual.getSourceId()).isEqualTo("SRC-1");
        assertThat(actual.getDefendantId()).isEqualTo(DEFENDANT_ID);
        assertThat(actual.getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(actual.getCustodyLocation()).isEqualTo("Prison");
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
        assertThat(actual.getDateOfBirth()).isEqualTo("encrypted-dob");
        assertThat(actual.getAddressLine1()).isEqualTo("encrypted-address-1");
        assertThat(actual.getAddressLine2()).isEqualTo("encrypted-address-2");
        assertThat(actual.getAddressLine3()).isEqualTo("encrypted-address-3");
        assertThat(actual.getAddressLine4()).isEqualTo("encrypted-address-4");
        assertThat(actual.getAddressLine5()).isEqualTo("encrypted-address-5");
        assertThat(actual.getPostCode()).isEqualTo("encrypted-postcode");
    }
}
