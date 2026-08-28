package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Daily scheduled purge of dead-letter messages older than the configured retention.
 *
 * <p>Mirrors service-cp-crime-hearing-results-document-subscription's DeadLetterPurgeService —
 * Azure Service Bus does not expire dead-lettered messages on its own, so without this they sit
 * in the DLQ indefinitely. Runs off the startup thread (not {@code @PostConstruct}) so a large
 * drain can never block application readiness/liveness probes. Purges blindly by age, same as
 * HRDS — no "reviewed/resolved" marker check.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterPurgeService {

    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_EMPTY_RECEIVES = 3;

    private final ServiceBusClientFactory clientFactory;
    private final ClockService clockService;

    @Value("${dead-letter.purge.retention-days}")
    private int retentionDays;

    @Scheduled(cron = "${dead-letter.purge.cron}")
    public void purgeOldDeadLetters() {
        log.info("DeadLetterPurge starting — removing dead-letter messages older than {} days from queue:{}",
                retentionDays, ServiceBusProperties.QUEUE_NAME);
        final int count = clearDeadLetterQueue(retentionDays);
        log.info("DeadLetterPurge removed {} dead-letter messages older than {} days from queue:{}",
                count, retentionDays, ServiceBusProperties.QUEUE_NAME);
    }

    private int clearDeadLetterQueue(final int olderThanDays) {
        final OffsetDateTime cutoff = clockService.nowOffsetUTC().minusDays(olderThanDays);
        final Set<Long> skipped = new HashSet<>();
        int count = 0;
        try (ServiceBusReceiverClient receiver = clientFactory.deadLetterReceiverClient()) {
            ServiceBusReceivedMessage message = nextMessage(receiver);
            while (message != null) {
                if (skipped.contains(message.getSequenceNumber())) {
                    receiver.abandon(message);
                    break;
                }
                final OffsetDateTime enqueuedAt = message.getEnqueuedTime();
                if (enqueuedAt.isBefore(cutoff)) {
                    log.info("Clearing DLQ message id:{} enqueuedAt:{}", message.getMessageId(), enqueuedAt);
                    receiver.complete(message);
                    count++;
                } else {
                    receiver.abandon(message);
                    skipped.add(message.getSequenceNumber());
                }
                message = nextMessage(receiver);
            }
        }
        return count;
    }

    /**
     * Receives the next dead-letter message, retrying on empty receives. The first receive often
     * times out while the AMQP link is still being established, so a single empty result does not
     * mean the queue is drained — only give up after {@link #MAX_EMPTY_RECEIVES} empty receives.
     */
    private ServiceBusReceivedMessage nextMessage(final ServiceBusReceiverClient receiver) {
        ServiceBusReceivedMessage message = null;
        for (int attempt = 0; attempt < MAX_EMPTY_RECEIVES && message == null; attempt++) {
            final Iterator<ServiceBusReceivedMessage> it = receiver.receiveMessages(1, RECEIVE_TIMEOUT).iterator();
            if (it.hasNext()) {
                message = it.next();
            }
        }
        return message;
    }
}
