package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPJudicialResultPromptRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID JUDICIAL_RESULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PROMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Autowired
    private CPCaseHearingRepository cpCaseHearingRepository;

    @Autowired
    private CPVersionRepository cpVersionRepository;

    @Autowired
    private CPOffenceRepository cpOffenceRepository;

    @Autowired
    private CPJudicialResultRepository cpJudicialResultRepository;

    @Autowired
    private CPJudicialResultPromptRepository cpJudicialResultPromptRepository;

    @Transactional
    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        saveParents();

        final CPJudicialResultPromptEntity entity = CPJudicialResultPromptEntity.builder()
                .id(PROMPT_ID)
                .judicialResultId(JUDICIAL_RESULT_ID)
                .label("Prison")
                .value("HMP Leeds")
                .promptReference("prisonOrganisationName")
                .type("TEXT")
                .build();

        cpJudicialResultPromptRepository.save(entity);

        final Optional<CPJudicialResultPromptEntity> found = cpJudicialResultPromptRepository.findById(PROMPT_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getJudicialResultId()).isEqualTo(JUDICIAL_RESULT_ID);
        assertThat(found.get().getLabel()).isEqualTo("Prison");
        assertThat(found.get().getValue()).isEqualTo("HMP Leeds");
        assertThat(found.get().getPromptReference()).isEqualTo("prisonOrganisationName");
        assertThat(found.get().getType()).isEqualTo("TEXT");
    }

    @Transactional
    @Test
    void findByJudicialResultId_should_returnMatchingPrompts() {
        saveParents();
        final UUID judicialResultId = UUID.fromString("00000000-0000-0000-0000-000000000089");
        cpJudicialResultRepository.save(CPJudicialResultEntity.builder()
                .id(judicialResultId)
                .offenceId(OFFENCE_ID)
                .resultCode("3120")
                .build());
        final CPJudicialResultPromptEntity prompt = CPJudicialResultPromptEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000090"))
                .judicialResultId(judicialResultId).promptReference("prisonOrganisationName").build();
        cpJudicialResultPromptRepository.save(prompt);

        final List<CPJudicialResultPromptEntity> found = cpJudicialResultPromptRepository.findByJudicialResultId(judicialResultId);

        assertThat(found).extracting(CPJudicialResultPromptEntity::getPromptReference).containsExactly("prisonOrganisationName");
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
        cpJudicialResultRepository.save(CPJudicialResultEntity.builder()
                .id(JUDICIAL_RESULT_ID)
                .offenceId(OFFENCE_ID)
                .resultCode("3120")
                .build());
    }
}
