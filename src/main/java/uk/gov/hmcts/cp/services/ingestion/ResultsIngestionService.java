package uk.gov.hmcts.cp.services.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedCacheClient;
import uk.gov.hmcts.cp.clients.ResultsClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.pcrcompute.CPNowSubscription;
import uk.gov.hmcts.cp.domain.pcrcompute.CPVocabulary;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.exceptions.NoOrderedDateFoundException;
import uk.gov.hmcts.cp.mappers.CPHearingResultEntityMapper;
import uk.gov.hmcts.cp.services.ClockService;
import uk.gov.hmcts.cp.services.pcrcompute.CPResultsPcrFilter;
import uk.gov.hmcts.cp.services.pcrcompute.CPVocabularyService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fetches a hearing's results and decides which defendants need a PCR (delegating the actual
 * gate to CPResultsPcrFilter) — all persistence is delegated to CPEntityPersistenceService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsIngestionService {

    public static final int MAX_COMPLETENESS_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

    private final HearingResultedCacheClient cacheClient;
    private final ResultsClient resultsClient;
    private final ObjectMapper objectMapper;
    private final CPVocabularyService vocabularyService;
    private final CPResultsPcrFilter pcrFilter;
    private final CPHearingResultEntityMapper entityMapper;
    private final ClockService clockService;
    private final CPEntityPersistenceService persistenceService;

    public HearingDetailsResponse ingestHearingResults(final UUID hearingId, final LocalDate hearingDay) {
        for (int attempt = 1; attempt <= MAX_COMPLETENESS_RETRIES; attempt++) {
            final Optional<HearingDetailsResponse> response = fetchIfComplete(hearingId, hearingDay);
            if (response.isPresent()) {
                return response.get();
            }
            log.warn("Incomplete hearing details for hearingId:{} on attempt {}/{} — viewstore may not have caught up yet",
                    hearingId, attempt, MAX_COMPLETENESS_RETRIES);
            if (attempt < MAX_COMPLETENESS_RETRIES) {
                sleepUninterruptibly(backoffFor(attempt));
            }
        }
        throw new IncompleteHearingDetailsException(hearingId);
    }

    public HearingDetailsResponse ingestHearingResultsOnce(final UUID hearingId, final LocalDate hearingDay) {
        return fetchIfComplete(hearingId, hearingDay).orElseThrow(() -> new IncompleteHearingDetailsException(hearingId));
    }

    private Optional<HearingDetailsResponse> fetchIfComplete(final UUID hearingId, final LocalDate hearingDay) {
        final HearingDetailsResponse response = cacheClient.get(hearingId, hearingDay)
                .map(this::deserializeCachedHearingResults)
                .orElseGet(() -> resultsClient.getHearingDetails(hearingId));
        return isComplete(response) ? Optional.of(response) : Optional.empty();
    }

    /* default */ Duration backoffFor(final int attempt) {
        return INITIAL_BACKOFF.multipliedBy((long) Math.pow(2, attempt - 1));
    }

    /* default */ void sleepUninterruptibly(final Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional
    public void ingestAndPersist(final UUID hearingId, final LocalDate hearingDay) {
        persist(hearingId, ingestHearingResults(hearingId, hearingDay));
    }

    @Transactional
    public void ingestAndPersistOnce(final UUID hearingId, final LocalDate hearingDay) {
        persist(hearingId, ingestHearingResultsOnce(hearingId, hearingDay));
    }

    private void persist(final UUID hearingId, final HearingDetailsResponse hearingDetails) {
        final HearingDetail hearing = hearingDetails.getHearing();
        final Instant sharedTime = hearingDetails.getSharedTime();
        final LocalDate activeAt = resolveActiveAt(hearing, hearingId);
        final List<CPNowSubscription> subscriptions = pcrFilter.fetchPrisonCourtRegisterSubscriptions(activeAt);
        Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
                .forEach(c -> processProsecutionCase(c, hearing, hearingId, sharedTime, subscriptions));
        processApplicationOnlyDefendants(hearing, hearingId, sharedTime, subscriptions);
    }

    // Handles a defendant only reachable via hearing.courtApplications — no matching
    // hearing.prosecutionCases entry at all (design doc 2026-09-02). alreadyProcessed is seeded
    // with every prosecution-case-driven defendantId and grown as each application-only
    // defendant is handled, so the same (hearingId, defendantId) is never persisted twice —
    // whether they're reached via both paths, or via more than one court application.
    private void processApplicationOnlyDefendants(final HearingDetail hearing, final UUID hearingId,
                                                    final Instant sharedTime, final List<CPNowSubscription> subscriptions) {
        final Set<String> alreadyProcessed = new HashSet<>(prosecutionCaseDefendantIds(hearing));
        Stream.ofNullable(hearing.getCourtApplications()).flatMap(List::stream)
                .forEach(application -> processCourtApplication(application, hearing, hearingId, sharedTime, subscriptions, alreadyProcessed));
    }

    private Set<String> prosecutionCaseDefendantIds(final HearingDetail hearing) {
        return Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
                .flatMap(c -> c.getDefendants().stream())
                .map(Defendant::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void processCourtApplication(final CourtApplication application, final HearingDetail hearing, final UUID hearingId,
                                          final Instant sharedTime, final List<CPNowSubscription> subscriptions,
                                          final Set<String> alreadyProcessed) {
        entityMapper.applicationOnlyDefendant(application)
                .filter(defendant -> alreadyProcessed.add(defendant.getId()))
                .filter(defendant -> isPcrRequired(defendant, hearing, hearingId, subscriptions))
                .ifPresent(defendant -> persistApplicationOnlyDefendant(defendant, application, hearing, hearingId, sharedTime));
    }

    private void persistApplicationOnlyDefendant(final Defendant defendant, final CourtApplication application, final HearingDetail hearing,
                                                  final UUID hearingId, final Instant sharedTime) {
        final UUID caseHearingId = persistenceService.findOrCreateCaseHearing(application.getApplicationReference(), hearing, hearingId);
        final String defendantType = entityMapper.defendantType(application, defendant.getMasterDefendantId());
        persistCPEntitySet(defendant, hearing, caseHearingId, sharedTime, defendantType);
    }

    private void processProsecutionCase(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                         final UUID hearingId, final Instant sharedTime, final List<CPNowSubscription> subscriptions) {
        final List<Defendant> requiredDefendants = prosecutionCase.getDefendants().stream()
                .filter(defendant -> isPcrRequired(defendant, hearing, hearingId, subscriptions))
                .toList();
        if (requiredDefendants.isEmpty()) {
            return;
        }
        final UUID caseHearingId = persistenceService.findOrCreateCaseHearing(prosecutionCase, hearing, hearingId);
        requiredDefendants.forEach(defendant -> persistCPEntitySet(defendant, hearing, caseHearingId, sharedTime));
    }

    private boolean isPcrRequired(final Defendant defendant, final HearingDetail hearing, final UUID hearingId,
                                   final List<CPNowSubscription> subscriptions) {
        final CPVocabulary vocabulary = vocabularyService.compute(defendant, hearing);
        final List<JudicialResult> eligibleResults = pcrFilter.excludePublishedForNows(entityMapper.eligibleResults(defendant, hearing));
        final boolean required = pcrFilter.isPrisonCourtRegisterRequired(vocabulary, eligibleResults, subscriptions);
        if (!required) {
            log.info("PCR not required for hearingId:{} defendantId:{} — skipping", hearingId, defendant.getId());
        }
        return required;
    }

    private void persistCPEntitySet(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                     final Instant sharedTime) {
        final OffsetDateTime createdAt = clockService.nowOffsetUTC();
        persistenceService.persist(defendant, hearing, caseHearingId, sharedTime, createdAt, createdAt.plusDays(30));
    }

    private void persistCPEntitySet(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                     final Instant sharedTime, final String defendantType) {
        final OffsetDateTime createdAt = clockService.nowOffsetUTC();
        persistenceService.persist(defendant, hearing, caseHearingId, sharedTime, createdAt, createdAt.plusDays(30), defendantType);
    }

    private LocalDate resolveActiveAt(final HearingDetail hearing, final UUID hearingId) {
        final Stream<LocalDate> fromProsecutionCases = Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
                .flatMap(c -> c.getDefendants().stream())
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream())
                .map(JudicialResult::getOrderedDate);
        final Stream<LocalDate> fromCourtApplications = Stream.ofNullable(hearing.getCourtApplications()).flatMap(List::stream)
                .flatMap(this::orderedDatesOf);
        return Stream.concat(fromProsecutionCases, fromCourtApplications)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new NoOrderedDateFoundException(hearingId));
    }

    private Stream<LocalDate> orderedDatesOf(final CourtApplication application) {
        final Stream<LocalDate> ownResults = Stream.ofNullable(application.getJudicialResults()).flatMap(List::stream)
                .map(JudicialResult::getOrderedDate);
        final Stream<LocalDate> linkedOffenceResults = Stream.ofNullable(application.getCourtApplicationCases()).flatMap(List::stream)
                .flatMap(c -> Stream.ofNullable(c.getOffences()).flatMap(List::stream))
                .flatMap(o -> Stream.ofNullable(o.getJudicialResults()).flatMap(List::stream))
                .map(JudicialResult::getOrderedDate);
        return Stream.concat(ownResults, linkedOffenceResults);
    }

    private HearingDetailsResponse deserializeCachedHearingResults(final String cachedJson) {
        try {
            return objectMapper.readValue(cachedJson, HearingDetailsResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Malformed cached hearing-result payload", e);
        }
    }

    private boolean isComplete(final HearingDetailsResponse response) {
        final HearingDetail hearing = response == null ? null : response.getHearing();
        return hearing != null && (isNotEmpty(hearing.getProsecutionCases()) || isNotEmpty(hearing.getCourtApplications()));
    }

    private boolean isNotEmpty(final List<?> list) {
        return list != null && !list.isEmpty();
    }
}