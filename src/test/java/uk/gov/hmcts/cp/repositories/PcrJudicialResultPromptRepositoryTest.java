package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.PcrCaseHearingEntity;
import uk.gov.hmcts.cp.entities.PcrJudicialResultEntity;
import uk.gov.hmcts.cp.entities.PcrJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.PcrOffenceEntity;
import uk.gov.hmcts.cp.entities.PcrVersionEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PcrJudicialResultPromptRepositoryTest extends RepositoryIntegrationTestBase {

    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_PK = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OFFENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID JUDICIAL_RESULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PROMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Autowired
    private PcrCaseHearingRepository pcrCaseHearingRepository;

    @Autowired
    private PcrVersionRepository pcrVersionRepository;

    @Autowired
    private PcrOffenceRepository pcrOffenceRepository;

    @Autowired
    private PcrJudicialResultRepository pcrJudicialResultRepository;

    @Autowired
    private PcrJudicialResultPromptRepository pcrJudicialResultPromptRepository;

    @Test
    void save_should_persistAndReturnEveryField_whenFindById() {
        saveParents();

        final PcrJudicialResultPromptEntity entity = PcrJudicialResultPromptEntity.builder()
                .id(PROMPT_ID)
                .judicialResultId(JUDICIAL_RESULT_ID)
                .label("Prison")
                .value("HMP Leeds")
                .promptReference("prisonOrganisationName")
                .type("TEXT")
                .build();

        pcrJudicialResultPromptRepository.save(entity);
        flushAndClear();

        final Optional<PcrJudicialResultPromptEntity> found = pcrJudicialResultPromptRepository.findById(PROMPT_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getJudicialResultId()).isEqualTo(JUDICIAL_RESULT_ID);
        assertThat(found.get().getLabel()).isEqualTo("Prison");
        assertThat(found.get().getValue()).isEqualTo("HMP Leeds");
        assertThat(found.get().getPromptReference()).isEqualTo("prisonOrganisationName");
        assertThat(found.get().getType()).isEqualTo("TEXT");
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
        pcrJudicialResultRepository.save(PcrJudicialResultEntity.builder()
                .id(JUDICIAL_RESULT_ID)
                .offenceId(OFFENCE_ID)
                .resultCode("3120")
                .build());
    }
}
