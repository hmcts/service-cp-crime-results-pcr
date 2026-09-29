package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterPurgeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final int RETENTION_DAYS = 30;

    @Mock
    private ServiceBusClientFactory clientFactory;
    @Spy
    private ClockService clockService = new ClockService(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @InjectMocks
    private DeadLetterPurgeService purgeService;

    @Mock
    private ServiceBusReceiverClient receiverClient;

    private void givenRetentionDays(final int days) {
        ReflectionTestUtils.setField(purgeService, "retentionDays", days);
    }

    @Test
    void purgeOldDeadLetters_should_complete_messages_older_than_retention() {
        givenRetentionDays(RETENTION_DAYS);
        final ServiceBusReceivedMessage oldMessage = mockMessageEnqueuedAt(FIXED_NOW.minusSeconds(40L * 86400), 1L);
        when(clientFactory.deadLetterReceiverClient()).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
                .thenReturn(IterableStream.of(List.of(oldMessage)))
                .thenReturn(IterableStream.of(Collections.emptyList()));

        purgeService.purgeOldDeadLetters();

        verify(receiverClient).complete(oldMessage);
        verify(receiverClient).close();
    }

    @Test
    void purgeOldDeadLetters_should_abandon_messages_within_retention() {
        givenRetentionDays(RETENTION_DAYS);
        final ServiceBusReceivedMessage recentMessage = mockMessageEnqueuedAt(FIXED_NOW.minusSeconds(2L * 86400), 1L);
        when(clientFactory.deadLetterReceiverClient()).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
                .thenReturn(IterableStream.of(List.of(recentMessage)))
                .thenReturn(IterableStream.of(Collections.emptyList()));

        purgeService.purgeOldDeadLetters();

        verify(receiverClient, never()).complete(any());
        verify(receiverClient).abandon(recentMessage);
        verify(receiverClient).close();
    }

    @Test
    void purgeOldDeadLetters_should_stop_when_skipped_sequence_number_seen_again() {
        givenRetentionDays(RETENTION_DAYS);
        final ServiceBusReceivedMessage recentMessage = mockMessageEnqueuedAt(FIXED_NOW.minusSeconds(1L * 86400), 99L);
        when(clientFactory.deadLetterReceiverClient()).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
                .thenReturn(IterableStream.of(List.of(recentMessage)))
                .thenReturn(IterableStream.of(List.of(recentMessage)));

        purgeService.purgeOldDeadLetters();

        verify(receiverClient, never()).complete(any());
        verify(receiverClient).close();
    }

    @Test
    void purgeOldDeadLetters_should_drain_messages_arriving_after_an_initial_empty_receive() {
        // The DLQ may hold messages, but the FIRST receive can return empty because the AMQP link
        // is still being established - the clear must not give up on a single empty receive.
        givenRetentionDays(0);
        final ServiceBusReceivedMessage oldMessage = mockMessageEnqueuedAt(FIXED_NOW.minusSeconds(10L * 86400), 1L);
        when(clientFactory.deadLetterReceiverClient()).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
                .thenReturn(IterableStream.of(Collections.emptyList()))
                .thenReturn(IterableStream.of(List.of(oldMessage)))
                .thenReturn(IterableStream.of(Collections.emptyList()));

        purgeService.purgeOldDeadLetters();

        verify(receiverClient).complete(oldMessage);
        verify(receiverClient).close();
    }

    @Test
    void purgeOldDeadLetters_should_completeNothing_whenDlqIsEmpty() {
        givenRetentionDays(RETENTION_DAYS);
        when(clientFactory.deadLetterReceiverClient()).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any())).thenReturn(IterableStream.of(Collections.emptyList()));

        purgeService.purgeOldDeadLetters();

        verify(receiverClient, never()).complete(any());
        verify(receiverClient).close();
    }

    private ServiceBusReceivedMessage mockMessageEnqueuedAt(final Instant enqueuedAt, final long sequenceNumber) {
        final ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        when(message.getEnqueuedTime()).thenReturn(OffsetDateTime.ofInstant(enqueuedAt, ZoneOffset.UTC));
        when(message.getSequenceNumber()).thenReturn(sequenceNumber);
        lenient().when(message.getMessageId()).thenReturn("msg-" + sequenceNumber);
        return message;
    }
}
