package uk.gov.hmcts.cp.services;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedCacheClient;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.clients.ResultsClient;
import uk.gov.hmcts.cp.config.RetryServiceConfig;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;
import uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.exceptions.NoOrderedDateFoundException;
import uk.gov.hmcts.cp.mappers.CPVersionEntityMapper;
import uk.gov.hmcts.cp.mappers.CPVersionWriteBundle;
import uk.gov.hmcts.cp.repositories.CPCaseHearingRepository;
import uk.gov.hmcts.cp.repositories.CPCaseMarkerRepository;
import uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository;
import uk.gov.hmcts.cp.repositories.CPJudicialResultRepository;
import uk.gov.hmcts.cp.repositories.CPOffenceRepository;
import uk.gov.hmcts.cp.repositories.CPVersionRepository;
import uk.gov.hmcts.cp.services.orchestrator.CPResultsPcrOrchestrator;
import uk.gov.hmcts.cp.services.orchestrator.CPVocabularyService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsIngestionServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";
    private static final HearingResultedPointer POINTER = new HearingResultedPointer(HEARING_ID, HEARING_DAY, "userId");

    @Mock
    private HearingResultedCacheClient cacheClient;
    @Mock
    private ResultsClient resultsClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private HearingResultedServiceBusClientFactory clientFactory;
    @Mock
    private ServiceBusReceivedMessageContext context;
    @Mock
    private ServiceBusReceivedMessage message;
    @Mock
    private ServiceBusSenderClient senderClient;
    @Spy
    private RetryServiceConfig retryServiceConfig =
            new RetryServiceConfig(List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2)), 3);
    @Mock
    private CPVocabularyService vocabularyService;
    @Mock
    private CPResultsPcrOrchestrator orchestrator;
    @Mock
    private CPVersionEntityMapper entityMapper;
    @Spy
    private ClockService clockService = new ClockService(Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC));
    @Mock
    private CPCaseHearingRepository caseHearingRepository;
    @Mock
    private CPCaseMarkerRepository caseMarkerRepository;
    @Mock
    private CPVersionRepository versionRepository;
    @Mock
    private CPCourtApplicationRepository courtApplicationRepository;
    @Mock
    private CPOffenceRepository offenceRepository;
    @Mock
    private CPJudicialResultRepository judicialResultRepository;
    @Mock
    private CPJudicialResultPromptRepository judicialResultPromptRepository;

    @InjectMocks
    private ResultsIngestionService ingestionService;

    @Test
    void ingest_should_returnCachedPayload_whenRedisHit() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY))
                .thenReturn(Optional.of("{\"hearing\":{\"prosecutionCases\":[{\"id\":\"case-1\"}]}}"));

        final HearingDetailsResponse result = ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
        verify(resultsClient, never()).getHearingDetails(any(UUID.class));
    }

    @Test
    void ingest_should_throwIllegalStateException_whenCachedPayloadIsMalformed() {
        // No HTTP status here — this path never runs inside a request, only the Service
        // Bus consumer, which just treats it as another "genuinely wrong" dead-letter case.
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.of("not-json"));

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ingest_should_fetchViaRest_whenRedisMiss_andFirstResponseIsComplete() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(completeResponse());

        final HearingDetailsResponse result = ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
    }

    @Test
    void ingest_should_throwIncompleteHearingDetailsException_whenFirstResponseIsIncomplete() {
        // Single-tier retry, matching HRDS's shape: no in-process loop — one incomplete
        // response fails fast and hands off to escalateOrDeadLetter's Service Bus escalation.
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
    }

    @Test
    void escalateOrDeadLetter_should_completeMessageAndSendRetryMessage_whenUnderMaxRetries() {
        when(context.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(new HashMap<>());
        when(clientFactory.senderClient()).thenReturn(senderClient);

        ingestionService.escalateOrDeadLetter(context, POINTER);

        verify(context).complete();
        verify(context, never()).deadLetter();
        final ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(senderClient).sendMessage(captor.capture());
        final ServiceBusMessage sent = captor.getValue();
        assertThat(sent.getApplicationProperties()).containsEntry("retryCount", 1);
        assertThat(sent.getScheduledEnqueueTime()).isAfter(OffsetDateTime.now().plusSeconds(25));
    }

    @Test
    void escalateOrDeadLetter_should_deadLetter_whenMaxScheduledRetriesExceeded() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("retryCount", 3);
        when(context.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(properties);

        ingestionService.escalateOrDeadLetter(context, POINTER);

        verify(context).deadLetter();
        verify(context, never()).complete();
        verify(clientFactory, never()).senderClient();
    }

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000044");
    private static final CPVocabulary VOCABULARY = CPVocabulary.builder()
            .prosecutorMajorCreditor(List.of()).nonProsecutorMajorCreditor(List.of()).build();

    @Test
    void ingestAndPersist_should_createCaseHearingAndPersistVersion_whenRequiredAndNotYetCreated() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.empty());
        final CPCaseHearingEntity caseHearingEntity = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(entityMapper.toCaseHearingEntity(any(), any(), eq(HEARING_ID), any())).thenReturn(caseHearingEntity);
        when(entityMapper.toCaseMarkerEntities(any(), eq(CASE_HEARING_ID))).thenReturn(List.of());
        final CPVersionWriteBundle bundle = emptyBundle();
        when(entityMapper.toWriteBundle(any(), any(), eq(CASE_HEARING_ID), any(), any())).thenReturn(bundle);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository).save(caseHearingEntity);
        verify(versionRepository).save(bundle.version());
        verify(courtApplicationRepository).saveAll(bundle.courtApplications());
        verify(offenceRepository).saveAll(bundle.offences());
        verify(judicialResultRepository).saveAll(bundle.judicialResults());
        verify(judicialResultPromptRepository).saveAll(bundle.judicialResultPrompts());
    }

    @Test
    void ingestAndPersist_should_reuseExistingCaseHearing_whenAlreadyFound() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        final CPCaseHearingEntity existing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.of(existing));
        when(entityMapper.toWriteBundle(any(), any(), eq(CASE_HEARING_ID), any(), any())).thenReturn(emptyBundle());

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository, never()).save(any());
        verify(caseMarkerRepository, never()).saveAll(any());
    }

    @Test
    void ingestAndPersist_should_skipDefendant_whenPcrNotRequired() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(false);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository, never()).findByCaseUrnAndHearingId(any(), any());
        verify(versionRepository, never()).save(any());
    }

    @Test
    void ingestAndPersist_should_findCaseHearingOnce_whenTwoDefendantsShareTheSameCase() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithTwoDefendantsOnOneCase());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.empty());
        final CPCaseHearingEntity caseHearingEntity = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(entityMapper.toCaseHearingEntity(any(), any(), eq(HEARING_ID), any())).thenReturn(caseHearingEntity);
        when(entityMapper.toCaseMarkerEntities(any(), eq(CASE_HEARING_ID))).thenReturn(List.of());
        when(entityMapper.toWriteBundle(any(), any(), eq(CASE_HEARING_ID), any(), any())).thenReturn(emptyBundle());

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository, times(1)).findByCaseUrnAndHearingId(CASE_URN, HEARING_ID);
        verify(caseHearingRepository, times(1)).save(caseHearingEntity);
        verify(versionRepository, times(2)).save(any());
    }

    private HearingDetailsResponse hearingWithTwoDefendantsOnOneCase() {
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").orderedDate(LocalDate.of(2026, 7, 15)).judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().judicialResults(List.of(result)).build();
        final Defendant defendantOne = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final Defendant defendantTwo = Defendant.builder()
                .id("22222222-2222-2222-2222-222222222222")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(defendantOne, defendantTwo))
                .build();
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .courtCentre(CourtCentre.builder().build())
                        .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of())
                        .build())
                .build();
    }

    @Test
    void ingestAndPersist_should_throwNoOrderedDateFoundException_whenNoResultHasOrderedDate() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithNoOrderedDate());

        assertThatThrownBy(() -> ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY))
                .isInstanceOf(NoOrderedDateFoundException.class);

        verify(vocabularyService, never()).compute(any(), any());
    }

    private CPVersionWriteBundle emptyBundle() {
        return new CPVersionWriteBundle(
                CPVersionEntity.builder().cpVersionPk(UUID.fromString("00000000-0000-0000-0000-000000000055")).build(),
                List.of(), List.of(), List.of(), List.of());
    }

    private HearingDetailsResponse hearingWithOneDefendant() {
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").orderedDate(LocalDate.of(2026, 7, 15)).judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(defendant))
                .build();
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .courtCentre(CourtCentre.builder().build())
                        .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of())
                        .build())
                .build();
    }

    private HearingDetailsResponse hearingWithNoOrderedDate() {
        final Defendant defendant = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(defendant))
                .build();
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of())
                        .build())
                .build();
    }

    private HearingDetailsResponse completeResponse() {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .prosecutionCases(List.of(HearingDetailsResponse.ProsecutionCase.builder().id("case-1").build()))
                        .build())
                .build();
    }

    private HearingDetailsResponse incompleteResponse() {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .prosecutionCases(List.of())
                        .build())
                .build();
    }
}