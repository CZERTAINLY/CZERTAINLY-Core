package com.otilm.core.cbom.pqc;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.core.cbom.asset.identity.AssetNormalizer;
import com.otilm.core.cbom.asset.identity.IdentityTables;
import com.otilm.core.cluster.ClusterOperationSynchronizer;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.model.cbom.PqcStaleVerdictRow;
import com.otilm.core.service.writer.cbom.CryptoAssetPqcVerdictWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sweep's control flow, without a database.
 *
 * <p>
 * These are the paths an integration test cannot reach cheaply: the per-sweep cap, the disabled switch, a contended
 * lock, an aborting batch write and a row whose evaluation throws. Each one is a decision about what the sweep reports
 * to an operator, and all of them were previously asserted only by reading the code.
 */
class PqcVerdictSweeperTest {

    private final CryptoAssetRepository repository = mock(CryptoAssetRepository.class);
    private final CryptoAssetPqcVerdictWriter writer = mock(CryptoAssetPqcVerdictWriter.class);
    private final ClusterOperationSynchronizer synchronizer = mock(ClusterOperationSynchronizer.class);

    @Test
    void aDisabledSweepDoesNotEvenTakeTheLock() {
        PqcVerdictSweeper sweeper = sweeper(5, 0);

        PqcVerdictSweeper.SweepOutcome outcome = sweeper.sweep();

        assertThat(outcome.ran()).isFalse();
        verify(synchronizer, never()).tryLock(any());
    }

    @Test
    void aContendedSweepReportsSkippedAndReadsNothing() {
        when(synchronizer.tryLock(ClusterOperationSynchronizer.Operation.CRYPTO_ASSET_PQC_SWEEP)).thenReturn(false);
        PqcVerdictSweeper sweeper = sweeper(5, 10);

        assertThat(sweeper.sweep().ran()).isFalse();
        verify(repository, never()).findStaleVerdictRows(anyInt(), any(), any());
    }

    /** The cap bounds how long the outer transaction stays open, so it must stop the loop even with work left. */
    @Test
    void theSweepStopsAtThePerSweepCap() {
        lockHeld();
        when(repository.findStaleVerdictRows(anyInt(), any(), any())).thenAnswer(call -> rows(5));
        when(writer.applyStaleBatch(any(), anyInt())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());

        PqcVerdictSweeper.SweepOutcome outcome = sweeper(5, 3).sweep();

        assertThat(outcome.batches()).isEqualTo(3);
        assertThat(outcome.evaluated()).isEqualTo(15);
        assertThat(outcome.aborted()).isFalse();
    }

    /** A short batch means the work list is exhausted; the loop must not ask again. */
    @Test
    void aShortBatchEndsTheSweep() {
        lockHeld();
        when(repository.findStaleVerdictRows(anyInt(), any(), any())).thenReturn(rows(2)).thenReturn(List.of());
        when(writer.applyStaleBatch(any(), anyInt())).thenReturn(2);

        assertThat(sweeper(5, 10).sweep().batches()).isEqualTo(1);
    }

    /**
     * A batch write that rolls back leaves rows stale. Reporting a successful run would hide that behind the rows the
     * earlier batches did write.
     */
    @Test
    void anAbortingBatchWriteIsReportedRatherThanSwallowed() {
        lockHeld();
        when(repository.findStaleVerdictRows(anyInt(), any(), any())).thenAnswer(call -> rows(5));
        when(writer.applyStaleBatch(any(), anyInt())).thenThrow(new IllegalStateException("deadlock detected"));

        PqcVerdictSweeper.SweepOutcome outcome = sweeper(5, 10).sweep();

        assertThat(outcome.aborted()).isTrue();
        assertThat(outcome.written()).isZero();
    }

    /**
     * A row that cannot be evaluated is stamped, not skipped. Skipping keeps its old generation, so it returns at the
     * head of the work list on every sweep and, with a cap, starves everything behind it.
     *
     * <p>
     * The payload carries a value the column mapper cannot serialize, so {@code evaluate} throws before any rule runs;
     * a payload the evaluator merely reads as absent would evaluate cleanly and prove nothing about the catch.
     */
    @Test
    void aRowThatCannotBeEvaluatedIsStampedSoTheSweepAdvances() {
        lockHeld();
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public String getType() {
                throw new IllegalStateException("unreadable payload");
            }
        };
        List<PqcStaleVerdictRow> poison = List
                .of(new PqcStaleVerdictRow(UUID.randomUUID(), CryptographicAssetType.ALGORITHM, "boom", null, null,
                        null, null, null, null, null, null, Map.of("relatedCryptoMaterialProperties", unserializable),
                        OffsetDateTime.now()));
        when(repository.findStaleVerdictRows(anyInt(), any(), any())).thenReturn(poison).thenReturn(List.of());
        when(writer.applyStaleBatch(any(), anyInt())).thenReturn(1);

        PqcVerdictSweeper.SweepOutcome outcome = sweeper(5, 10).sweep();

        assertThat(outcome.evaluated()).describedAs("the fixture must actually throw").isZero();
        assertThat(outcome.failed()).isEqualTo(1);
        ArgumentCaptor<List<PqcVerdictWrite>> batch = ArgumentCaptor.captor();
        verify(writer).applyStaleBatch(batch.capture(), anyInt());
        assertThat(batch.getValue()).hasSize(1);
        assertThat(batch.getValue().get(0).assetUuid()).isEqualTo(poison.get(0).uuid());
        assertThat(batch.getValue().get(0).decision().ruleId()).isEqualTo("EVALUATION-FAILED");
        assertThat(batch.getValue().get(0).decision().verdict()).isEqualTo(PqcVerdict.UNKNOWN);
    }

    private void lockHeld() {
        when(synchronizer.tryLock(ClusterOperationSynchronizer.Operation.CRYPTO_ASSET_PQC_SWEEP)).thenReturn(true);
    }

    private static List<PqcStaleVerdictRow> rows(int count) {
        List<PqcStaleVerdictRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows
                    .add(new PqcStaleVerdictRow(UUID.randomUUID(), CryptographicAssetType.ALGORITHM, "RSA", null, "rsa",
                            null, "2048", null, null, null, null, null, OffsetDateTime.now()));
        }
        return rows;
    }

    private PqcVerdictSweeper sweeper(int batchSize, int maxBatches) {
        return new PqcVerdictSweeper(repository, writer, new PqcEvaluator(new AssetNormalizer(IdentityTables.load())),
                synchronizer, new SimpleMeterRegistry(), new PqcSweepProperties(batchSize, maxBatches));
    }
}
