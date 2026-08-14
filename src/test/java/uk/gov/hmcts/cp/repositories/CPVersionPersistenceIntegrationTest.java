package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.mappers.CPHearingResultEntityMapper;
import uk.gov.hmcts.cp.mappers.CPEntitySet;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPVersionPersistenceIntegrationTest extends RepositoryIntegrationTestBase {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000066");

    @Autowired
    private CPHearingResultEntityMapper mapper;
    @Autowired
    private CPCaseHearingRepository caseHearingRepository;
    @Autowired
    private CPCaseMarkerRepository caseMarkerRepository;
    @Autowired
    private CPVersionRepository versionRepository;
    @Autowired
    private CPOffenceRepository offenceRepository;
    @Autowired
    private CPJudicialResultRepository judicialResultRepository;
    @Autowired
    private CPJudicialResultPromptRepository judicialResultPromptRepository;

    @Transactional
    @Test
    void persistedGraph_should_beReadableWithCorrectForeignKeys_afterFullWrite() {
        final ProsecutionCase prosecutionCase = prosecutionCaseWithOneOffenceOneResultOnePrompt();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .prosecutionCases(List.of(prosecutionCase))
                .courtApplications(List.of())
                .build();
        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        final CPCaseHearingEntity caseHearing = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, createdAt);
        caseHearingRepository.save(caseHearing);
        caseMarkerRepository.saveAll(mapper.toCaseMarkerEntities(prosecutionCase, caseHearing.getId()));
        final CPEntitySet bundle = mapper.toWriteBundle(
                prosecutionCase.getDefendants().get(0), hearing, caseHearing.getId(), null, createdAt, createdAt.plusDays(30));
        versionRepository.save(bundle.version());
        offenceRepository.saveAll(bundle.offences());
        judicialResultRepository.saveAll(bundle.judicialResults());
        judicialResultPromptRepository.saveAll(bundle.judicialResultPrompts());

        assertThat(caseMarkerRepository.findAll()).extracting("caseHearingId").contains(caseHearing.getId());
        assertThat(versionRepository.findById(bundle.version().getCpVersionPk())).isPresent();
        final var savedOffence = offenceRepository.findById(bundle.offences().get(0).getId()).orElseThrow();
        assertThat(savedOffence.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        final var savedResult = judicialResultRepository.findById(bundle.judicialResults().get(0).getId()).orElseThrow();
        assertThat(savedResult.getOffenceId()).isEqualTo(savedOffence.getId());
        final var savedPrompt = judicialResultPromptRepository.findById(bundle.judicialResultPrompts().get(0).getId()).orElseThrow();
        assertThat(savedPrompt.getJudicialResultId()).isEqualTo(savedResult.getId());
    }

    private ProsecutionCase prosecutionCaseWithOneOffenceOneResultOnePrompt() {
        final JudicialResultPrompt prompt = JudicialResultPrompt.builder()
                .promptReference("prisonOrganisationName").value("HMP Dovegate").build();
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").label("Imprisonment")
                .isFinancialResult(false).isConvictedResult(true)
                .orderedDate(LocalDate.of(2026, 7, 15))
                .judicialResultPrompts(List.of(prompt))
                .build();
        final Offence offence = Offence.builder().offenceCode("TH68001").judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN("ABCD1234567").build())
                .caseMarkers(List.of(CaseMarker.builder().markerTypeCode("DomesticViolence").build()))
                .defendants(List.of(defendant))
                .build();
    }
}