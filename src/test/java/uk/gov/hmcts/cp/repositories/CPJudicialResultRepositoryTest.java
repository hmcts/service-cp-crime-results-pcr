package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPJudicialResultRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID JUDICIAL_RESULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPVersionRepository cpVersionRepository;

    @Autowired
    private CPOffenceRepository cpOffenceRepository;

    @Autowired
    private CPCourtApplicationRepository cpCourtApplicationRepository;

    @Autowired
    private CPJudicialResultRepository cpJudicialResultRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenParentedByOffence() {
        saveParents();

        final CPJudicialResultEntity entity = CPJudicialResultEntity.builder()
                .id(JUDICIAL_RESULT_ID)
                .offenceId(OFFENCE_ID)
                .resultCode("3120")
                .resultText("Fine imposed")
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

        cpJudicialResultRepository.save(entity);

        final Optional<CPJudicialResultEntity> found = cpJudicialResultRepository.findById(JUDICIAL_RESULT_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getOffenceId()).isEqualTo(OFFENCE_ID);
        assertThat(found.get().getCourtApplicationId()).isNull();
        assertThat(found.get().getResultCode()).isEqualTo("3120");
        assertThat(found.get().getResultText()).isEqualTo("Fine imposed");
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

    @Transactional
    @Test
    void findByOffenceId_should_returnMatchingResults() {
        saveParents();
        final UUID offenceId = UUID.fromString("00000000-0000-0000-0000-000000000084");
        cpOffenceRepository.save(CPOffenceEntity.builder()
                .id(offenceId)
                .versionPk(VERSION_PK)
                .code("TH68001")
                .build());
        final CPJudicialResultEntity result = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000085"))
                .offenceId(offenceId).resultCode("1200").build();
        cpJudicialResultRepository.save(result);

        final List<CPJudicialResultEntity> found = cpJudicialResultRepository.findByOffenceId(offenceId);

        assertThat(found).extracting(CPJudicialResultEntity::getResultCode).containsExactly("1200");
    }

    @Transactional
    @Test
    void findByCourtApplicationId_should_returnMatchingResults() {
        saveParents();
        final UUID courtApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000086");
        cpCourtApplicationRepository.save(CPCourtApplicationEntity.builder()
                .id(courtApplicationId)
                .versionPk(VERSION_PK)
                .reference("APP-1")
                .build());
        final CPJudicialResultEntity result = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000087"))
                .courtApplicationId(courtApplicationId).resultCode("1201").build();
        cpJudicialResultRepository.save(result);

        final List<CPJudicialResultEntity> found = cpJudicialResultRepository.findByCourtApplicationId(courtApplicationId);

        assertThat(found).extracting(CPJudicialResultEntity::getResultCode).containsExactly("1201");
    }

    @Transactional
    @Test
    void findByVersionPk_should_returnDefendantAndCaseLevelResults() {
        saveParents();
        final CPJudicialResultEntity defendantResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000088"))
                .versionPk(VERSION_PK).level("D").resultCode("D1").build();
        final CPJudicialResultEntity caseResult = CPJudicialResultEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000089"))
                .versionPk(VERSION_PK).level("C").resultCode("C1").build();
        cpJudicialResultRepository.save(defendantResult);
        cpJudicialResultRepository.save(caseResult);

        final List<CPJudicialResultEntity> found = cpJudicialResultRepository.findByVersionPk(VERSION_PK);

        assertThat(found).extracting(CPJudicialResultEntity::getResultCode).containsExactlyInAnyOrder("D1", "C1");
        assertThat(found).extracting(CPJudicialResultEntity::getOffenceId).containsOnlyNulls();
        assertThat(found).extracting(CPJudicialResultEntity::getCourtApplicationId).containsOnlyNulls();
    }

    private void saveParents() {
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
        cpOffenceRepository.save(CPOffenceEntity.builder()
                .id(OFFENCE_ID)
                .versionPk(VERSION_PK)
                .code("TH68001")
                .build());
    }
}
