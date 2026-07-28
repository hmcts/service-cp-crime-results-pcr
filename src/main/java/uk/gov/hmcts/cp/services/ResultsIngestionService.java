package uk.gov.hmcts.cp.services;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.clients.HearingResultedCacheClient;
import uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory;
import uk.gov.hmcts.cp.clients.ResultsClient;
import uk.gov.hmcts.cp.config.RetryServiceConfig;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingResultedPointer;
import uk.gov.hmcts.cp.domain.orchestrator.CPNowSubscription;
import uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsIngestionService {

    private static final String RETRY_COUNT_PROPERTY = "retryCount";

    private final HearingResultedCacheClient cacheClient;
    private final ResultsClient resultsClient;
    private final ObjectMapper objectMapper;
    private final HearingResultedServiceBusClientFactory clientFactory;
    private final RetryServiceConfig retryServiceConfig;
    private final CPVocabularyService vocabularyService;
    private final CPResultsPcrOrchestrator orchestrator;
    private final CPVersionEntityMapper entityMapper;
    private final ClockService clockService;
    private final CPCaseHearingRepository caseHearingRepository;
    private final CPCaseMarkerRepository caseMarkerRepository;
    private final CPVersionRepository versionRepository;
    private final CPCourtApplicationRepository courtApplicationRepository;
    private final CPOffenceRepository offenceRepository;
    private final CPJudicialResultRepository judicialResultRepository;
    private final CPJudicialResultPromptRepository judicialResultPromptRepository;

    public HearingDetailsResponse ingestHearingResults(final UUID hearingId, final String hearingDay) {
        return cacheClient.get(hearingId, hearingDay)
                .map(this::deserializeCachedHearingResults)
                .orElseGet(() -> getHearingResults(hearingId));
    }

    @Transactional
    public void ingestAndPersist(final UUID hearingId, final String hearingDay) {
        final HearingDetailsResponse hearingDetails = ingestHearingResults(hearingId, hearingDay);
        final HearingDetail hearing = hearingDetails.getHearing();
        final LocalDate activeAt = resolveActiveAt(hearing, hearingId);
        final List<CPNowSubscription> subscriptions = orchestrator.fetchPrisonCourtRegisterSubscriptions(activeAt);
        hearing.getProsecutionCases().forEach(c -> processProsecutionCase(c, hearing, hearingId, subscriptions));
    }

    private void processProsecutionCase(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                         final UUID hearingId, final List<CPNowSubscription> subscriptions) {
        UUID caseHearingId = null;
        for (final Defendant defendant : prosecutionCase.getDefendants()) {
            if (!isPcrRequired(defendant, hearing, subscriptions)) {
                log.info("PCR not required for hearingId:{} defendantId:{} — skipping", hearingId, defendant.getId());
                continue;
            }
            caseHearingId = caseHearingId == null ? findOrCreateCaseHearing(prosecutionCase, hearing, hearingId) : caseHearingId;
            persistVersion(defendant, hearing, caseHearingId);
        }
    }

    private boolean isPcrRequired(final Defendant defendant, final HearingDetail hearing, final List<CPNowSubscription> subscriptions) {
        final CPVocabulary vocabulary = vocabularyService.compute(defendant, hearing);
        final List<JudicialResult> eligibleResults = orchestrator.excludePublishedForNows(entityMapper.eligibleResults(defendant, hearing));
        return orchestrator.isPrisonCourtRegisterRequired(vocabulary, eligibleResults, subscriptions);
    }

    private UUID findOrCreateCaseHearing(final ProsecutionCase prosecutionCase, final HearingDetail hearing, final UUID hearingId) {
        final String caseUrn = prosecutionCase.getProsecutionCaseIdentifier().getCaseURN();
        return caseHearingRepository.findByCaseUrnAndHearingId(caseUrn, hearingId)
                .map(CPCaseHearingEntity::getId)
                .orElseGet(() -> createCaseHearing(prosecutionCase, hearing, hearingId));
    }

    private UUID createCaseHearing(final ProsecutionCase prosecutionCase, final HearingDetail hearing, final UUID hearingId) {
        final CPCaseHearingEntity entity = entityMapper.toCaseHearingEntity(prosecutionCase, hearing, hearingId, clockService.nowOffsetUTC());
        caseHearingRepository.save(entity);
        caseMarkerRepository.saveAll(entityMapper.toCaseMarkerEntities(prosecutionCase, entity.getId()));
        return entity.getId();
    }

    private void persistVersion(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId) {
        final OffsetDateTime createdAt = clockService.nowOffsetUTC();
        final CPVersionWriteBundle bundle = entityMapper.toWriteBundle(defendant, hearing, caseHearingId, createdAt, createdAt.plusDays(30));
        versionRepository.save(bundle.version());
        courtApplicationRepository.saveAll(bundle.courtApplications());
        offenceRepository.saveAll(bundle.offences());
        judicialResultRepository.saveAll(bundle.judicialResults());
        judicialResultPromptRepository.saveAll(bundle.judicialResultPrompts());
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

    private HearingDetailsResponse getHearingResults(final UUID hearingId) {
        final HearingDetailsResponse response = resultsClient.getHearingDetails(hearingId);
        if (isComplete(response)) {
            return response;
        }
        log.warn("Incomplete hearing details for hearingId:{} — viewstore may not have caught up yet", hearingId);
        throw new IncompleteHearingDetailsException(hearingId);
    }

    private boolean isComplete(final HearingDetailsResponse response) {
        return response != null
                && response.getHearing() != null
                && response.getHearing().getProsecutionCases() != null
                && !response.getHearing().getProsecutionCases().isEmpty();
    }

    public void escalateOrDeadLetter(final ServiceBusReceivedMessageContext context, final HearingResultedPointer hearingResultedPointer) {
        final int retryCount = retryCountOf(context.getMessage()) + 1;
        if (retryCount > retryServiceConfig.maxTries()) {
            log.error("Giving up on hearingId:{} after {} scheduled retries — dead-lettering explicitly",
                    hearingResultedPointer.hearingId(), retryCount);
            context.deadLetter();
            return;
        }
        context.complete();
        final Duration delay = retryServiceConfig.delayFor(retryCount);
        log.warn("Scheduling retry {}/{} for hearingId:{} in {}", retryCount, retryServiceConfig.maxTries(), hearingResultedPointer.hearingId(), delay);
        sendRetryMessage(hearingResultedPointer, retryCount, delay);
    }

    private void sendRetryMessage(final HearingResultedPointer pointer, final int retryCount, final Duration delay) {
        final ServiceBusMessage retryMessage = newRetryMessage(pointer, retryCount, delay);
        try (ServiceBusSenderClient sender = clientFactory.senderClient()) {
            sender.sendMessage(retryMessage);
        }
    }

    private ServiceBusMessage newRetryMessage(final HearingResultedPointer hearingResultedPointer, final int retryCount, final Duration delay) {
        final ServiceBusMessage message = new ServiceBusMessage(objectMapper.writeValueAsString(hearingResultedPointer));
        message.getApplicationProperties().put(RETRY_COUNT_PROPERTY, retryCount);
        message.setScheduledEnqueueTime(OffsetDateTime.now().plus(delay));
        return message;
    }

    private int retryCountOf(final ServiceBusReceivedMessage message) {
        final Object value = message.getApplicationProperties().get(RETRY_COUNT_PROPERTY);
        return value == null ? 0 : (int) value;
    }
}