package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.PcrCaseHearingEntity;
import uk.gov.hmcts.cp.entities.PcrOffenceEntity;
import uk.gov.hmcts.cp.entities.PcrVersionEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PcrOffenceRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");

    @Autowired
    private PcrCaseHearingRepository pcrCaseHearingRepository;

    @Autowired
    private PcrVersionRepository pcrVersionRepository;

    @Autowired
    private PcrOffenceRepository pcrOffenceRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenParentedByVersion() {
        pcrCaseHearingRepository.save(PcrCaseHearingEntity.builder()
                .id(CASE_HEARING_ID)
                .caseUrn("ABCD1234567")
                .hearingId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        pcrVersionRepository.save(PcrVersionEntity.builder()
                .pcrVersionPk(VERSION_PK)
                .defendantId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
                .caseHearingId(CASE_HEARING_ID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
                .build());

        final PcrOffenceEntity entity = PcrOffenceEntity.builder()
                .id(OFFENCE_ID)
                .versionPk(VERSION_PK)
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

        pcrOffenceRepository.save(entity);
        flushAndClear();

        final Optional<PcrOffenceEntity> found = pcrOffenceRepository.findById(OFFENCE_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getVersionPk()).isEqualTo(VERSION_PK);
        assertThat(found.get().getCourtApplicationId()).isNull();
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
}
