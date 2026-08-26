package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.cluster.ClusterOperationSynchronizer;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.service.handler.discovery.DiscoveryProviderAdapter;
import com.otilm.core.service.handler.discovery.DiscoveryProviderAdapterFactory;
import com.otilm.core.service.handler.discovery.DiscoveryRunTerminator;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import com.otilm.core.util.DiscoveryRunMetaFixture;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link DiscoveryRunReaper#reap()}'s per-run behaviors, with collaborators mocked.
 *
 * <p>
 * <b>Unit scope:</b> what a real-DB test cannot exercise deterministically — the under-lock re-assertions (skip a run
 * that gained agenda rows or escaped its stop), the post-commit connector cancel, and the per-run failure isolation
 * (one run's exception must not abort the rest of the batch).
 *
 * <p>
 * <b>Integration scope:</b> the selection predicates and terminal outcomes run against real Postgres in
 * {@link com.otilm.core.integration.discovery.DiscoveryWorkSweepITest}.
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryRunReaperUnitTest {

    @Mock
    private DiscoveryRepository discoveryRepository;
    @Mock
    private DiscoveryWorkRepository workRepository;
    @Mock
    private DiscoveryWorkWriter workWriter;
    @Mock
    private DiscoveryProviderAdapterFactory adapterFactory;
    @Mock
    private DiscoveryProviderAdapter adapter;
    @Mock
    private TransactionHandler transactionHandler;
    @Mock
    private ClusterOperationSynchronizer clusterSynchronizer;

    private DiscoveryRunReaper reaper;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void setUp() {
        // A real terminator, not a mock: the reaper delegates the terminal mutation to it, and that mutation
        // is what these tests assert on. Its collaborators are unused by applyTerminalState.
        reaper = new DiscoveryRunReaper(discoveryRepository, workRepository, workWriter, adapterFactory,
                transactionHandler, clusterSynchronizer,
                new DiscoveryRunTerminator(discoveryRepository, workWriter, transactionHandler), Duration.ofMinutes(5),
                Duration.ofDays(7));
        // Execute the transactional lambdas inline so the real selection/reap logic runs under the test.
        lenient()
                .when(transactionHandler.runInNewTransaction(any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient()
                .when(clusterSynchronizer.tryLock(ClusterOperationSynchronizer.Operation.DISCOVERY_WORK_SWEEP))
                .thenReturn(true);
        lenient().when(adapterFactory.forDiscovery(any())).thenReturn(adapter);
    }

    @Test
    void skipsAllWorkWhenAdvisoryLockNotAcquired() {
        when(clusterSynchronizer.tryLock(ClusterOperationSynchronizer.Operation.DISCOVERY_WORK_SWEEP))
                .thenReturn(false);

        reaper.reap();

        verify(discoveryRepository, never()).findWorkLostRunUuids(any(), any(), any());
        verify(discoveryRepository, never()).findExpiredStoppedRunUuids(any(), any());
        verify(discoveryRepository, never()).findWithLockByUuid(any());
    }

    @Test
    void workLost_skipsRunThatGainedAgendaRowsUnderLock() {
        Discovery run = run(DiscoveryStatus.IN_PROGRESS);
        selections(List.of(run.getUuid()), List.of());
        when(discoveryRepository.findWithLockByUuid(run.getUuid())).thenReturn(Optional.of(run));
        // Between selection and locking the run gained agenda rows: it is driven again.
        when(workRepository.existsByDiscoveryUuid(run.getUuid())).thenReturn(true);

        reaper.reap();

        assertThat(run.getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
        assertThat(run.getEndTime()).isNull();
    }

    @Test
    void workLost_oneRunFailureDoesNotAbortTheBatch() {
        Discovery healthy = run(DiscoveryStatus.IN_PROGRESS);
        UUID failing = UUID.randomUUID();
        selections(List.of(failing, healthy.getUuid()), List.of());
        when(discoveryRepository.findWithLockByUuid(failing)).thenThrow(new RuntimeException("lock timeout"));
        when(discoveryRepository.findWithLockByUuid(healthy.getUuid())).thenReturn(Optional.of(healthy));
        when(workRepository.existsByDiscoveryUuid(healthy.getUuid())).thenReturn(false);

        assertThatCode(() -> reaper.reap()).doesNotThrowAnyException();

        assertThat(healthy.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        assertThat(healthy.getRunMessages())
                .as("a reaped run must carry the same ending in its log as one a worker ended")
                .containsExactly("Discovery work lost; the run can no longer be driven");
    }

    @Test
    void workLost_cancelsOnTheConnectorOnlyWhenRunContextExists() {
        Discovery withContext = run(DiscoveryStatus.IN_PROGRESS);
        withContext.setRunMeta(DiscoveryRunMetaFixture.runMeta("cursor", "abc"));
        Discovery withoutContext = run(DiscoveryStatus.IN_PROGRESS);
        selections(List.of(withContext.getUuid(), withoutContext.getUuid()), List.of());
        when(discoveryRepository.findWithLockByUuid(withContext.getUuid())).thenReturn(Optional.of(withContext));
        when(discoveryRepository.findWithLockByUuid(withoutContext.getUuid())).thenReturn(Optional.of(withoutContext));

        reaper.reap();

        assertThat(withContext.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        assertThat(withoutContext.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        // Only the run with a persisted connector context has anything the connector could drop.
        verify(adapter).cancel(withContext);
        verify(adapter, never()).cancel(withoutContext);
    }

    @Test
    void stopExpired_skipsResumedRunAndCancelsNothingOnTheConnector() {
        // The run escaped its stop between selection and action: status moved off STOPPED, stoppedAt stale.
        Discovery resumed = run(DiscoveryStatus.IN_PROGRESS);
        resumed.setStoppedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
        selections(List.of(), List.of(resumed.getUuid()));
        when(discoveryRepository.findWithLockByUuid(resumed.getUuid())).thenReturn(Optional.of(resumed));

        reaper.reap();

        // No terminal transition committed, so nothing may reach the connector.
        verifyNoInteractions(adapterFactory);
        assertThat(resumed.getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
        verify(workWriter, never()).deleteForRun(any());
    }

    @Test
    void stopExpired_oneRunFailureDoesNotAbortTheBatch() {
        Discovery healthy = expiredStoppedRun();
        UUID failing = UUID.randomUUID();
        selections(List.of(), List.of(failing, healthy.getUuid()));
        when(discoveryRepository.findWithLockByUuid(failing)).thenThrow(new RuntimeException("lock timeout"));
        when(discoveryRepository.findWithLockByUuid(healthy.getUuid())).thenReturn(Optional.of(healthy));

        assertThatCode(() -> reaper.reap()).doesNotThrowAnyException();

        assertThat(healthy.getStatus()).isEqualTo(DiscoveryStatus.CANCELLED);
        verify(workWriter).deleteForRun(healthy.getUuid());
        // The cancel fired only after the terminal transition, replaying the pre-wipe run context.
        verify(adapter).cancel(healthy);
        assertThat(healthy.getRunMeta()).isNotNull();
    }

    @Test
    void stopExpired_connectorCancelFailureDoesNotBlockTheLocalCancel() {
        Discovery expired = expiredStoppedRun();
        selections(List.of(), List.of(expired.getUuid()));
        when(discoveryRepository.findWithLockByUuid(expired.getUuid())).thenReturn(Optional.of(expired));
        when(adapterFactory.forDiscovery(any())).thenThrow(new IllegalStateException("not implemented"));

        assertThatCode(() -> reaper.reap()).doesNotThrowAnyException();

        assertThat(expired.getStatus()).isEqualTo(DiscoveryStatus.CANCELLED);
    }

    private void selections(List<UUID> workLost, List<UUID> stopExpired) {
        when(discoveryRepository.findWorkLostRunUuids(any(), any(), any())).thenReturn(workLost);
        when(discoveryRepository.findExpiredStoppedRunUuids(any(), any())).thenReturn(stopExpired);
    }

    private static Discovery run(DiscoveryStatus status) {
        Discovery run = new Discovery();
        run.setUuid(UUID.randomUUID());
        run.setStatus(status);
        run.setConnectorStatus(status);
        run.setConnectorInterfaceUuid(UUID.randomUUID());
        return run;
    }

    private static Discovery expiredStoppedRun() {
        Discovery run = run(DiscoveryStatus.STOPPED);
        run.setStoppedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));
        run.setRunMeta(DiscoveryRunMetaFixture.runMeta("cursor", "abc"));
        return run;
    }
}
