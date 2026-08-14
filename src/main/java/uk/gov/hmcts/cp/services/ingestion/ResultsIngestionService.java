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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fetches a hearing's results and decides which defendants need a PCR (delegating the actual
 * gate to CPResultsPcrFilter) — all persistence is delegated to CPEntityPersistenceService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsIngestionService {

    private static final int MAX_COMPLETENESS_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

    private final HearingResultedCacheClient cacheClient;
    private final ResultsClient resultsClient;
    private final ObjectMapper objectMapper;
    private final CPVocabularyService vocabularyService;
    private final CPResultsPcrFilter pcrFilter;
    private final CPHearingResultEntityMapper entityMapper;
    private final ClockService clockService;
    private final CPEntityPersistenceService persistenceService;

    public HearingDetailsResponse ingestHearingResults(final UUID hearingId, final String hearingDay) {
        for (int attempt = 1; attempt <= MAX_COMPLETENESS_RETRIES; attempt++) {
            final HearingDetailsResponse response = cacheClient.get(hearingId, hearingDay)
                    .map(this::deserializeCachedHearingResults)
                    .orElseGet(() -> resultsClient.getHearingDetails(hearingId));
            if (isComplete(response)) {
                return response;
            }
            log.warn("Incomplete hearing details for hearingId:{} on attempt {}/{} — viewstore may not have caught up yet",
                    hearingId, attempt, MAX_COMPLETENESS_RETRIES);
            if (attempt < MAX_COMPLETENESS_RETRIES) {
                sleepUninterruptibly(backoffFor(attempt));
            }
        }
        throw new IncompleteHearingDetailsException(hearingId);
    }

    private Duration backoffFor(final int attempt) {
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
    public void ingestAndPersist(final UUID hearingId, final String hearingDay) {
        final HearingDetailsResponse hearingDetails = ingestHearingResults(hearingId, hearingDay);
        final HearingDetail hearing = hearingDetails.getHearing();
        final Instant sharedTime = hearingDetails.getSharedTime();
        final LocalDate activeAt = resolveActiveAt(hearing, hearingId);
        final List<CPNowSubscription> subscriptions = pcrFilter.fetchPrisonCourtRegisterSubscriptions(activeAt);
        hearing.getProsecutionCases().forEach(c -> processProsecutionCase(c, hearing, hearingId, sharedTime, subscriptions));
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

    private LocalDate resolveActiveAt(final HearingDetail hearing, final UUID hearingId) {
        return hearing.getProsecutionCases().stream()
                .flatMap(c -> c.getDefendants().stream())
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream())
                .map(JudicialResult::getOrderedDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new NoOrderedDateFoundException(hearingId));
    }

    private HearingDetailsResponse deserializeCachedHearingResults(final String cachedJson) {
        try {
            return objectMapper.readValue(cachedJson, HearingDetailsResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Malformed cached hearing-result payload", e);
        }
    }

    private boolean isComplete(final HearingDetailsResponse response) {
        return response != null
                && response.getHearing() != null
                && response.getHearing().getProsecutionCases() != null
                && !response.getHearing().getProsecutionCases().isEmpty();
    }
}