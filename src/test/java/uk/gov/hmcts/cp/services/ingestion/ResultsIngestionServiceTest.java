package uk.gov.hmcts.cp.services.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedCacheClient;
import uk.gov.hmcts.cp.clients.ResultsClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplicationCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.pcrcompute.CPVocabulary;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.exceptions.NoOrderedDateFoundException;
import uk.gov.hmcts.cp.mappers.CPHearingResultEntityMapper;
import uk.gov.hmcts.cp.services.ClockService;
import uk.gov.hmcts.cp.services.pcrcompute.CPResultsPcrFilter;
import uk.gov.hmcts.cp.services.pcrcompute.CPVocabularyService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsIngestionServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final LocalDate HEARING_DAY = LocalDate.parse("2026-07-23");

    @Mock
    private HearingResultedCacheClient cacheClient;
    @Mock
    private ResultsClient resultsClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private CPVocabularyService vocabularyService;
    @Mock
    private CPResultsPcrFilter pcrFilter;
    @Mock
    private CPHearingResultEntityMapper entityMapper;
    @Spy
    private ClockService clockService = new ClockService(Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC));
    @Mock
    private CPEntityPersistenceService persistenceService;
    @Captor
    private ArgumentCaptor<Duration> durationCaptor;

    @Spy
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
    void ingest_should_throwIncompleteHearingDetailsException_whenCachedPayloadIsIncomplete() {
        doNothing().when(ingestionService).sleepUninterruptibly(any());
        when(cacheClient.get(HEARING_ID, HEARING_DAY))
                .thenReturn(Optional.of("{\"hearing\":{\"prosecutionCases\":[]}}"));

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

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
    void ingest_should_returnResponse_whenSecondRestAttemptIsComplete() {
        doNothing().when(ingestionService).sleepUninterruptibly(any());
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID))
                .thenReturn(incompleteResponse())
                .thenReturn(completeResponse());

        final HearingDetailsResponse result = ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
        verify(resultsClient, times(2)).getHearingDetails(HEARING_ID);
    }

    @Test
    void ingest_should_throwIncompleteHearingDetailsException_whenAllThreeAttemptsAreIncomplete() {
        doNothing().when(ingestionService).sleepUninterruptibly(any());
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

        verify(resultsClient, times(3)).getHearingDetails(HEARING_ID);
    }

    @Test
    void ingest_should_sleepWithExponentialBackoff_betweenRetries() {
        doNothing().when(ingestionService).sleepUninterruptibly(any());
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

        assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

        verify(ingestionService, times(2)).sleepUninterruptibly(durationCaptor.capture());
        assertThat(durationCaptor.getAllValues()).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void ingestHearingResultsOnce_should_returnResponse_whenComplete() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(completeResponse());

        final HearingDetailsResponse result = ingestionService.ingestHearingResultsOnce(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
    }

    @Test
    void ingestHearingResultsOnce_should_returnResponse_whenApplicationOnlyHearing_hasNoProsecutionCases() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(applicationOnlyResponse());

        final HearingDetailsResponse result = ingestionService.ingestHearingResultsOnce(HEARING_ID, HEARING_DAY);

        assertThat(result.getHearing().getCourtApplications()).hasSize(1);
        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
    }

    @Test
    void ingestHearingResultsOnce_should_throwImmediately_whenIncomplete_withoutRetrying() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

        assertThatThrownBy(() -> ingestionService.ingestHearingResultsOnce(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
        verify(ingestionService, never()).sleepUninterruptibly(any());
    }

    @Test
    void backoffFor_should_returnExponentialBackoff() {
        assertThat(ingestionService.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(ingestionService.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
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
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(persistenceService.findOrCreateCaseHearing(any(ProsecutionCase.class), any(), eq(HEARING_ID))).thenReturn(CASE_HEARING_ID);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService).findOrCreateCaseHearing(any(ProsecutionCase.class), any(), eq(HEARING_ID));
        verify(persistenceService).persist(any(), any(), eq(CASE_HEARING_ID), any(), any(), any());
    }

    @Test
    void ingestAndPersistOnce_should_createCaseHearingAndPersistVersion_whenComplete() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(persistenceService.findOrCreateCaseHearing(any(ProsecutionCase.class), any(), eq(HEARING_ID))).thenReturn(CASE_HEARING_ID);

        ingestionService.ingestAndPersistOnce(HEARING_ID, HEARING_DAY);

        verify(persistenceService).persist(any(), any(), eq(CASE_HEARING_ID), any(), any(), any());
    }

    @Test
    void ingestAndPersistOnce_should_throwIncompleteHearingDetailsException_withoutRetrying_whenIncomplete() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

        assertThatThrownBy(() -> ingestionService.ingestAndPersistOnce(HEARING_ID, HEARING_DAY))
                .isInstanceOf(IncompleteHearingDetailsException.class);

        verify(resultsClient, times(1)).getHearingDetails(HEARING_ID);
    }

    @Test
    void ingestAndPersist_should_skipDefendant_whenPcrNotRequired() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(false);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService, never()).findOrCreateCaseHearing(any(ProsecutionCase.class), any(), any());
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestAndPersist_should_findCaseHearingOnce_whenTwoDefendantsShareTheSameCase() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithTwoDefendantsOnOneCase());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(persistenceService.findOrCreateCaseHearing(any(ProsecutionCase.class), any(), eq(HEARING_ID))).thenReturn(CASE_HEARING_ID);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService, times(1)).findOrCreateCaseHearing(any(ProsecutionCase.class), any(), eq(HEARING_ID));
        verify(persistenceService, times(2)).persist(any(), any(), eq(CASE_HEARING_ID), any(), any(), any());
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

    private static final UUID APPLICATION_DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000055");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String APPLICATION_REFERENCE = "APP-REF-1";

    @Test
    void ingestAndPersist_should_persistApplicationOnlyDefendant_whenRequired() {
        final CourtApplication application = applicationWithOrderedDate();
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(applicationOnlyHearing(application));
        final Defendant syntheticDefendant = applicationOnlyDefendant();
        when(entityMapper.applicationOnlyDefendant(application)).thenReturn(Optional.of(syntheticDefendant));
        when(vocabularyService.compute(eq(syntheticDefendant), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(eq(syntheticDefendant), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(persistenceService.findOrCreateCaseHearing(eq(APPLICATION_REFERENCE), any(), eq(HEARING_ID), any())).thenReturn(CASE_HEARING_ID);
        when(entityMapper.defendantType(application, MASTER_DEFENDANT_ID)).thenReturn("Respondent");

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService).findOrCreateCaseHearing(eq(APPLICATION_REFERENCE), any(), eq(HEARING_ID), any());
        verify(persistenceService).persist(eq(syntheticDefendant), any(), eq(CASE_HEARING_ID), any(), any(), any(), eq("Respondent"));
    }

    @Test
    void ingestAndPersist_should_skipApplicationOnlyDefendant_whenPcrNotRequired() {
        final CourtApplication application = applicationWithOrderedDate();
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(applicationOnlyHearing(application));
        final Defendant syntheticDefendant = applicationOnlyDefendant();
        when(entityMapper.applicationOnlyDefendant(application)).thenReturn(Optional.of(syntheticDefendant));
        when(vocabularyService.compute(eq(syntheticDefendant), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(eq(syntheticDefendant), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(false);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService, never()).findOrCreateCaseHearing(any(String.class), any(), any(), any());
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestAndPersist_should_skipApplicationOnlyProcessing_whenNoDefendantResolved() {
        final CourtApplication application = applicationWithOrderedDate();
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(applicationOnlyHearing(application));
        when(entityMapper.applicationOnlyDefendant(application)).thenReturn(Optional.empty());

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService, never()).findOrCreateCaseHearing(any(String.class), any(), any(), any());
        verify(vocabularyService, never()).compute(any(), any());
    }

    @Test
    void ingestAndPersist_should_notReprocessApplicationOnlyDefendant_whenAlreadyReachedViaProsecutionCase() {
        final CourtApplication application = applicationWithOrderedDate();
        final JudicialResult caseResult = JudicialResult.builder()
                .cjsCode("1200").orderedDate(LocalDate.of(2026, 7, 15)).judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().judicialResults(List.of(caseResult)).build();
        final Defendant prosecutionCaseDefendant = Defendant.builder()
                .id(APPLICATION_DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(prosecutionCaseDefendant))
                .build();
        final HearingDetailsResponse response = HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .courtCentre(CourtCentre.builder().build())
                        .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of(application))
                        .build())
                .build();
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(response);
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(persistenceService.findOrCreateCaseHearing(any(ProsecutionCase.class), any(), eq(HEARING_ID))).thenReturn(CASE_HEARING_ID);
        when(entityMapper.applicationOnlyDefendant(application)).thenReturn(Optional.of(
                Defendant.builder().id(APPLICATION_DEFENDANT_ID.toString()).masterDefendantId(MASTER_DEFENDANT_ID)
                        .personDefendant(PersonDefendant.builder().build()).offences(List.of()).build()));

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(persistenceService, times(1)).persist(any(), any(), any(), any(), any(), any());
        verify(persistenceService, never()).findOrCreateCaseHearing(any(String.class), any(), any(), any());
    }

    @Test
    void ingestAndPersist_should_resolveActiveAt_fromLinkedOffenceOrderedDate_whenApplicationHasNoOwnJudicialResults() {
        final JudicialResult offenceResult = JudicialResult.builder()
                .cjsCode("1017").orderedDate(LocalDate.of(2026, 7, 15)).judicialResultPrompts(List.of()).build();
        final Offence linkedOffence = Offence.builder().judicialResults(List.of(offenceResult)).build();
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .applicationReference(APPLICATION_REFERENCE)
                .judicialResults(List.of())
                .courtApplicationCases(List.of(CourtApplicationCase.builder().offences(List.of(linkedOffence)).build()))
                .build();
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(applicationOnlyHearing(application));
        when(entityMapper.applicationOnlyDefendant(application)).thenReturn(Optional.of(applicationOnlyDefendant()));
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(pcrFilter.excludePublishedForNows(any())).thenReturn(List.of());
        when(pcrFilter.fetchPrisonCourtRegisterSubscriptions(any())).thenReturn(List.of());
        when(pcrFilter.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(persistenceService.findOrCreateCaseHearing(eq(APPLICATION_REFERENCE), any(), eq(HEARING_ID), any())).thenReturn(CASE_HEARING_ID);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(pcrFilter).fetchPrisonCourtRegisterSubscriptions(LocalDate.of(2026, 7, 15));
    }

    private CourtApplication applicationWithOrderedDate() {
        final JudicialResult applicationResult = JudicialResult.builder()
                .cjsCode("G").orderedDate(LocalDate.of(2026, 7, 15)).judicialResultPrompts(List.of()).build();
        return CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .applicationReference(APPLICATION_REFERENCE)
                .judicialResults(List.of(applicationResult))
                .courtApplicationCases(List.of())
                .build();
    }

    private HearingDetailsResponse applicationOnlyHearing(final CourtApplication application) {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .courtCentre(CourtCentre.builder().build())
                        .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                        .courtApplications(List.of(application))
                        .build())
                .build();
    }

    private Defendant applicationOnlyDefendant() {
        return Defendant.builder()
                .id(APPLICATION_DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
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

    private HearingDetailsResponse applicationOnlyResponse() {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .courtApplications(List.of(HearingDetailsResponse.CourtApplication.builder().id("app-1").build()))
                        .build())
                .build();
    }
}