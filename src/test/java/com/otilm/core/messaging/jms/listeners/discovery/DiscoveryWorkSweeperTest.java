package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.core.messaging.jms.producers.DiscoveryWorkProducer;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryWorkSweeperTest {

    @Mock
    private DiscoveryWorkClaimer workClaimer;
    @Mock
    private DiscoveryWorkProducer workProducer;
    @Mock
    private DiscoveryRunReaper runReaper;

    private static final int BATCH_SIZE = 200;

    private DiscoveryWorkSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new DiscoveryWorkSweeper(workClaimer, workProducer, runReaper, BATCH_SIZE, 10);
    }

    @Test
    void noDueRows_sendsNothing() {
        when(workClaimer.claimDueBatch(BATCH_SIZE)).thenReturn(List.of());

        sweeper.sweep();

        verifyNoInteractions(workProducer);
    }

    @Test
    void everySweep_reapsUndrivableRuns() {
        when(workClaimer.claimDueBatch(BATCH_SIZE)).thenReturn(List.of());

        sweeper.sweep();

        verify(runReaper).reap();
    }

    @Test
    void due_sendsClaimedMessagesOutsideTheClaim() {
        DiscoveryWorkMessage msg = new DiscoveryWorkMessage(UUID.randomUUID(), DiscoveryWorkType.STATUS, 2);
        // A partial batch (< batchSize) ends the loop after one round.
        when(workClaimer.claimDueBatch(BATCH_SIZE)).thenReturn(List.of(msg));

        sweeper.sweep();

        verify(workProducer).produceMessage(msg);
        // Claiming is the only transactional/lock-holding step; sends happen after it.
        verify(workClaimer).claimDueBatch(anyInt());
    }

    @Test
    void sendFailure_doesNotAbortTheSweep() {
        DiscoveryWorkMessage msg = new DiscoveryWorkMessage(UUID.randomUUID(), DiscoveryWorkType.DRAIN, 1);
        when(workClaimer.claimDueBatch(BATCH_SIZE)).thenReturn(List.of(msg));
        doThrow(new RuntimeException("broker down")).when(workProducer).produceMessage(msg);

        // One bad send must not propagate — the row is already rescheduled and retries when next due.
        sweeper.sweep();

        verify(workProducer).produceMessage(msg);
        verify(runReaper).reap();
    }
}
