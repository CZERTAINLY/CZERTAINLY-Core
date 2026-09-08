package com.otilm.core.integration.cbom;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.core.cbom.asset.AssetRowKeys;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.cbom.pqc.PqcDecision;
import com.otilm.core.cbom.pqc.PqcRuleset;
import com.otilm.core.cbom.pqc.PqcVerdictSweeper;
import com.otilm.core.cbom.pqc.PqcVerdictWrite;
import com.otilm.core.cluster.ClusterOperationSynchronizer;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.model.cbom.PqcStaleVerdictRow;
import com.otilm.core.service.writer.cbom.CryptoAssetPqcVerdictWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetSourceWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The re-evaluation sweep against real PostgreSQL.
 *
 * <p>
 * What it proves is the orchestration, not the SQL: the two-timestamp semantics are already pinned by
 * {@link CryptoAssetInventoryITest#aReEvaluationAdvancesEvaluatedAtButNotDecidedAt} and re-proving them here would only
 * assert that the same statement still does what that test says. What is new is the parts that can only be wrong in a
 * database -- the keyset cursor advancing across batches, the per-sweep cap, the guard refusing a row touched between
 * the sweep's read and its write, and the advisory lock admitting one sweeper.
 */
class CryptoAssetPqcSweepITest extends BaseSpringBootTest {

    private static final int AWAIT_TIMEOUT_SECONDS = 10;

    @Autowired
    private CryptoAssetRepository assetRepository;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @Autowired
    private PqcVerdictSweeper sweeper;

    @Autowired
    private CryptoAssetSourceWriter sourceWriter;

    @Autowired
    private CbomRepository cbomRepository;

    @Autowired
    private CryptoAssetPqcVerdictWriter verdictWriter;

    @Autowired
    private ClusterOperationSynchronizer synchronizer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService lockHolderThread = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopLockHolder() {
        lockHolderThread.shutdownNow();
    }

    @Test
    void aSweepRestampsEveryRowThatCarriesNoVerdictYet() {
        UUID rsa = upsert("RSA", "rsa", "2048");
        UUID aes = upsert("AES", "aes", "256");

        PqcVerdictSweeper.SweepOutcome outcome = sweeper.sweep();

        assertThat(outcome.ran()).isTrue();
        assertThat(outcome.evaluated()).isGreaterThanOrEqualTo(2);
        assertThat(verdictOf(rsa)).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(aes)).isEqualTo(PqcVerdict.READY);
        assertThat(asset(rsa).getPqcRulesetVersion()).isEqualTo(PqcRuleset.VERSION);
        assertThat(asset(rsa).getPqcEvaluatedAt()).isNotNull();
        assertThat(asset(rsa).getPqcDecidedAt()).isNotNull();
    }

    @Test
    void aSweepLeavesNoStaleRowBehind() {
        for (int i = 0; i < 12; i++) {
            upsert("RSA-" + i, "rsa", String.valueOf(2048 + i));
        }

        sweeper.sweep();

        assertThat(assetRepository.findStaleVerdictRows(PqcRuleset.VERSION, new UUID(0L, 0L), PageRequest.of(0, 100)))
                .describedAs("after a successful sweep, nothing may still carry a verdict below the shipped generation")
                .isEmpty();
    }

    /**
     * A verdict is not an identity: the sweep must leave the keying columns and their generation exactly as it found
     * them, or a re-evaluation would look like a re-keying to every consumer of {@code ruleset_version}.
     */
    @Test
    void aSweepTouchesNoIdentityColumn() {
        UUID uuid = upsert("RSA", "rsa", "2048");
        CryptoAsset before = asset(uuid);

        sweeper.sweep();

        CryptoAsset after = asset(uuid);
        assertThat(after.getIdentityKey()).isEqualTo(before.getIdentityKey());
        assertThat(after.getRulesetVersion()).isEqualTo(before.getRulesetVersion());
        assertThat(after.getAlgorithmFamily()).isEqualTo(before.getAlgorithmFamily());
    }

    /**
     * The window between the sweep's read and its write. Ingest can land a current-generation verdict in it, and the
     * guard is what stops the sweep overwriting that with one computed from the columns it read earlier -- a row that
     * would then look freshly evaluated and be wrong until the next generation bump, which is the shape of failure
     * nothing would find.
     */
    @Test
    void afresherVerdictIsNotOverwritten() {
        UUID uuid = upsert("RSA", "rsa", "2048");
        assetWriter
                .applyPqcVerdict(uuid, PqcVerdict.READY, "INGEST-WON", "written by ingest", PqcRuleset.VERSION, null);

        sweeper.sweep();

        assertThat(asset(uuid).getPqcRuleId())
                .describedAs("the sweep must not overwrite a verdict already at the shipped generation")
                .isEqualTo("INGEST-WON");
        assertThat(verdictOf(uuid)).isEqualTo(PqcVerdict.READY);
    }

    /**
     * The guard itself, exercised where the race actually happens.
     *
     * <p>
     * {@link #afresherVerdictIsNotOverwritten} writes the fresh verdict before the sweep runs, so the work list never
     * offers the row and the guard is never reached -- removing the guard's clauses would leave that test green. This
     * one calls the writer with a batch the sweep would have built from a stale read, after a fresher verdict has
     * landed, which is one of the two orderings the guard exists for.
     */
    @Test
    void theGuardedWriteRefusesABatchBuiltFromAStaleRead() {
        UUID uuid = upsert("RSA", "rsa", "2048");
        PqcVerdictWrite staleRead = new PqcVerdictWrite(uuid, staleRow(uuid).updated(), new PqcDecision(
                PqcVerdict.NOT_READY, "SWEEP-STALE", "computed from the columns read earlier", Map.of()));

        assetWriter
                .applyPqcVerdict(uuid, PqcVerdict.READY, "INGEST-WON", "written by ingest", PqcRuleset.VERSION, null);

        assertThat(verdictWriter.applyStaleBatch(List.of(staleRead), PqcRuleset.VERSION))
                .describedAs("the row is no longer stale, so the guarded update must write nothing")
                .isZero();
        assertThat(asset(uuid).getPqcRuleId()).isEqualTo("INGEST-WON");
    }

    /**
     * The other ordering: what lands in the window is a payload, not a verdict. A payload moves no generation, so a
     * guard on {@code pqc_ruleset_version} alone would write the verdict computed without it and stamp the row current
     * -- and a current row is offered to no later sweep, so the contradiction between verdict and payload would stand
     * until the next generation bump.
     */
    @Test
    void aPayloadLandingBetweenReadAndWriteKeepsTheRowOnTheWorkList() {
        UUID uuid = upsertMaterial("vault-key-2024");
        PqcStaleVerdictRow read = staleRow(uuid);
        assertThat(read.mergedCryptoProperties()).describedAs("read before any payload landed").isNull();
        PqcVerdictWrite fromRead = new PqcVerdictWrite(uuid, read.updated(), new PqcDecision(PqcVerdict.UNKNOWN,
                "SWEEP-NO-PAYLOAD", "computed before the payload landed", Map.of()));

        sourceWriter
                .upsertSource(uuid, cbom().getUuid(),
                        Map.of("relatedCryptoMaterialProperties", Map.of("type", "secret-key", "size", 64)), List.of(),
                        OffsetDateTime.now());

        assertThat(verdictWriter.applyStaleBatch(List.of(fromRead), PqcRuleset.VERSION))
                .describedAs(
                        "the row's inputs moved after the read, so the verdict computed from that read must not land")
                .isZero();
        assertThat(asset(uuid).getPqcRulesetVersion()).describedAs("still on the work list").isNull();

        sweeper.sweep();

        assertThat(asset(uuid).getPqcRuleId()).isEqualTo("MATERIAL-SYMMETRIC-WEAK");
        assertThat(asset(uuid).getPqcEvaluatedFields()).containsEntry("materialSize", 64);
    }

    /** The advisory lock admits one sweeper; a contended run skips rather than blocking or double-writing. */
    @Test
    void aContendedSweepSkipsRatherThanWaiting() throws Exception {
        upsert("RSA", "rsa", "2048");

        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> acquired = new CompletableFuture<>();
        Future<?> holder = lockHolderThread.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            acquired.complete(synchronizer.tryLock(ClusterOperationSynchronizer.Operation.CRYPTO_ASSET_PQC_SWEEP));
            try {
                release.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertThat(acquired.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        try {
            PqcVerdictSweeper.SweepOutcome contended = sweeper.sweep();
            assertThat(contended.ran()).describedAs("contended sweep must skip, not block").isFalse();
            assertThat(contended.written()).isZero();
        } finally {
            release.countDown();
        }
        holder.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(sweeper.sweep().ran()).describedAs("the released lock frees the next sweep").isTrue();
    }

    /**
     * The cursor, not an offset. A written row leaves the work list, so an offset would skip exactly as many unread
     * rows as the previous batch wrote -- this seeds more rows than one batch holds and asserts none was missed.
     */
    @Test
    void theSweepAdvancesAcrossBatchesWithoutSkippingARow() {
        for (int i = 0; i < 25; i++) {
            upsert("RSA-batch-" + i, "rsa", String.valueOf(1024 + i));
        }

        PqcVerdictSweeper.SweepOutcome outcome = sweeper.sweep();

        assertThat(outcome.batches())
                .describedAs("with the test batch size of 5 this must cross several boundaries; a single batch would "
                        + "let offset paging or a broken cursor pass")
                .isGreaterThan(1);
        assertThat(outcome.evaluated()).isGreaterThanOrEqualTo(25);
        assertThat(assetRepository.findStaleVerdictRows(PqcRuleset.VERSION, new UUID(0L, 0L), PageRequest.of(0, 100)))
                .isEmpty();
    }

    private UUID upsert(String name, String family, String parameterSet) {
        CryptoAssetIdentityFields fields = new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, name, null,
                family, "signature", parameterSet, null, null, null, null);
        return assetWriter.upsertIdentity(AssetRowKeys.forFields(fields), fields, null);
    }

    private UUID upsertMaterial(String name) {
        CryptoAssetIdentityFields fields = new CryptoAssetIdentityFields(CryptographicAssetType.RELATED_CRYPTO_MATERIAL,
                name, null, null, null, null, null, null, null, null);
        return assetWriter.upsertIdentity(AssetRowKeys.forFields(fields), fields, null);
    }

    private Cbom cbom() {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
        cbom.setVersion(1);
        cbom.setSpecVersion("1.7");
        return cbomRepository.save(cbom);
    }

    private PqcStaleVerdictRow staleRow(UUID uuid) {
        return assetRepository
                .findStaleVerdictRows(PqcRuleset.VERSION, new UUID(0L, 0L), PageRequest.of(0, 100))
                .stream()
                .filter(row -> row.uuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    private CryptoAsset asset(UUID uuid) {
        return assetRepository.findById(uuid).orElseThrow();
    }

    private PqcVerdict verdictOf(UUID uuid) {
        return asset(uuid).getPqcVerdict();
    }
}
