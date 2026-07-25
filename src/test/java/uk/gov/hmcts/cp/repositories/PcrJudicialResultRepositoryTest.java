package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.PcrCaseHearingEntity;
import uk.gov.hmcts.cp.entities.PcrJudicialResultEntity;
import uk.gov.hmcts.cp.entities.PcrOffenceEntity;
import uk.gov.hmcts.cp.entities.PcrVersionEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PcrJudicialResultRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID JUDICIAL_RESULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired
    private PcrCaseHearingRepository pcrCaseHearingRepository;

    @Autowired
    private PcrVersionRepository pcrVersionRepository;

    @Autowired
    private PcrOffenceRepository pcrOffenceRepository;

    @Autowired
    private PcrJudicialResultRepository pcrJudicialResultRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenParentedByOffence() {
        saveParents();

        final PcrJudicialResultEntity entity = PcrJudicialResultEntity.builder()
                .id(JUDICIAL_RESULT_ID)
                .offenceId(OFFENCE_ID)
                .resultCode("3120")
                .resultText("Fine imposed")
                .postHearingCustodyStatus("Released")
                .financial(true)
                .category("Financial")
                .convicted(true)
                .concurrent(false)
                .consecutiveToDate(LocalDate.of(2026, 8, 1))
                .consecutiveToCourtName("Leeds Crown Court")
                .fineAmount(new BigDecimal("250.00"))
                .imprisonmentPeriod("6 months")
                .totalCustodialPeriod("6 months")
                .build();

        pcrJudicialResultRepository.save(entity);
        flushAndClear();

        final Optional<PcrJudicialResultEntity> found = pcrJudicialResultRepository.findById(JUDICIAL_RESULT_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getOffenceId()).isEqualTo(OFFENCE_ID);
        assertThat(found.get().getCourtApplicationId()).isNull();
        assertThat(found.get().getResultCode()).isEqualTo("3120");
        assertThat(found.get().getResultText()).isEqualTo("Fine imposed");
        assertThat(found.get().getPostHearingCustodyStatus()).isEqualTo("Released");
        assertThat(found.get().getFinancial()).isTrue();
        assertThat(found.get().getCategory()).isEqualTo("Financial");
        assertThat(found.get().getConvicted()).isTrue();
        assertThat(found.get().getConcurrent()).isFalse();
        assertThat(found.get().getConsecutiveToDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(found.get().getConsecutiveToCourtName()).isEqualTo("Leeds Crown Court");
        assertThat(found.get().getFineAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(found.get().getImprisonmentPeriod()).isEqualTo("6 months");
        assertThat(found.get().getTotalCustodialPeriod()).isEqualTo("6 months");
    }

    private void saveParents() {
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
        pcrOffenceRepository.save(PcrOffenceEntity.builder()
                .id(OFFENCE_ID)
                .versionPk(VERSION_PK)
                .code("TH68001")
                .build());
    }
}
