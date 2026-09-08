package com.otilm.core.cbom.pqc;

import com.fasterxml.jackson.databind.JsonNode;
import com.otilm.core.cluster.ClusterOperationSynchronizer;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.model.cbom.PqcStaleVerdictRow;
import com.otilm.core.serialization.ObjectMapperFactory;
import com.otilm.core.service.writer.cbom.CryptoAssetPqcVerdictWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restamps every asset whose verdict predates {@link PqcRuleset#VERSION}, in batches.
 *
 * <p>
 * This transaction exists to hold the advisory lock, not to write: every write goes through
 * {@link CryptoAssetPqcVerdictWriter}'s {@code REQUIRES_NEW} batch, so row locks release per batch while the lock keeps
 * one node sweeping. The cap bounds how long this transaction stays open; the scheduler opens one above it that nothing
 * here caps.
 */
@Slf4j
@Component
public class PqcVerdictSweeper {

    /** Ordering is by uuid, so the nil uuid precedes every row. */
    private static final UUID BEFORE_FIRST = new UUID(0L, 0L);

    /**
     * What a row gets when evaluation throws: stamped current so the sweep moves past it instead of finding it at the
     * head of the work list forever. No evidence, because the inputs are what failed.
     */
    private static final PqcDecision EVALUATION_FAILED = new PqcDecision(
            com.otilm.api.model.core.cryptoasset.PqcVerdict.UNKNOWN, "EVALUATION-FAILED",
            "The rule set could not be evaluated against this asset's recorded properties", java.util.Map.of());

    private final CryptoAssetRepository assetRepository;
    private final CryptoAssetPqcVerdictWriter verdictWriter;
    private final PqcEvaluator evaluator;
    private final ClusterOperationSynchronizer clusterSynchronizer;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int maxBatchesPerSweep;

    public PqcVerdictSweeper(CryptoAssetRepository assetRepository, CryptoAssetPqcVerdictWriter verdictWriter,
            PqcEvaluator evaluator, ClusterOperationSynchronizer clusterSynchronizer, MeterRegistry meterRegistry,
            PqcSweepProperties properties) {
        this.assetRepository = assetRepository;
        this.verdictWriter = verdictWriter;
        this.evaluator = evaluator;
        this.clusterSynchronizer = clusterSynchronizer;
        this.meterRegistry = meterRegistry;
        this.batchSize = properties.batchSize();
        this.maxBatchesPerSweep = properties.maxBatchesPerSweep();
    }

    /** @return what the sweep did, for the job's history entry */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SweepOutcome sweep() {
        if (maxBatchesPerSweep <= 0) {
            log.debug("PQC verdict sweep disabled: max-batches-per-sweep is {}", maxBatchesPerSweep);
            return SweepOutcome.skipped();
        }
        if (!clusterSynchronizer.tryLock(ClusterOperationSynchronizer.Operation.CRYPTO_ASSET_PQC_SWEEP)) {
            log.debug("PQC verdict sweep skipped: another instance holds the lock");
            return SweepOutcome.skipped();
        }
        meterRegistry.counter("crypto_asset.pqc_sweep").increment();

        UUID cursor = BEFORE_FIRST;
        int evaluated = 0;
        int written = 0;
        int failed = 0;
        int batches = 0;
        boolean aborted = false;
        try {
            List<PqcStaleVerdictRow> rows;
            do {
                rows = assetRepository.findStaleVerdictRows(PqcRuleset.VERSION, cursor, PageRequest.of(0, batchSize));
                if (rows.isEmpty()) {
                    break;
                }
                cursor = rows.get(rows.size() - 1).uuid();
                List<PqcVerdictWrite> writes = new ArrayList<>(rows.size());
                for (PqcStaleVerdictRow row : rows) {
                    PqcDecision decision = decideOrRecordFailure(row);
                    if (decision == EVALUATION_FAILED) {
                        failed++;
                    } else {
                        evaluated++;
                    }
                    writes.add(new PqcVerdictWrite(row.uuid(), row.updated(), decision));
                }
                written += verdictWriter.applyStaleBatch(writes, PqcRuleset.VERSION);
                batches++;
            } while (rows.size() == batchSize && batches < maxBatchesPerSweep);

            if (rows.size() == batchSize && batches >= maxBatchesPerSweep) {
                log
                        .debug("PQC verdict sweep stopped at the per-sweep cap of {} batch(es); the remaining stale rows clear on the next sweep",
                                maxBatchesPerSweep);
            }
        } catch (RuntimeException e) {
            meterRegistry.counter("crypto_asset.pqc_sweep.aborted").increment();
            log.warn("PQC verdict sweep aborted after writing {} verdict(s); will retry next interval", written, e);
            aborted = true;
        }
        if (written > 0) {
            meterRegistry.counter("crypto_asset.pqc_sweep.written").increment(written);
            log.info("PQC verdict sweep restamped {} cryptographic asset(s)", written);
        }
        return new SweepOutcome(true, aborted, evaluated, written, failed, batches);
    }

    /**
     * The sentinel is returned by identity, so the caller can tell a stamped failure from a verdict without inspecting
     * it -- a row may legitimately evaluate to the same verdict and rule id that a failure records.
     */
    private PqcDecision decideOrRecordFailure(PqcStaleVerdictRow row) {
        try {
            return evaluate(row);
        } catch (RuntimeException e) {
            meterRegistry.counter("crypto_asset.pqc_sweep.evaluation_failed").increment();
            // The uuid, never the identity key: this line reaches an operator's log aggregator.
            log
                    .warn("PQC verdict evaluation failed for cryptographic asset {}; recording it as unevaluated",
                            row.uuid(), e);
            return EVALUATION_FAILED;
        }
    }

    private PqcDecision evaluate(PqcStaleVerdictRow row) {
        JsonNode merged = row.mergedCryptoProperties() == null
                ? null
                : ObjectMapperFactory.jsonColumn().valueToTree(row.mergedCryptoProperties());
        return evaluator
                .evaluate(evaluator.fromStoredRow(row.fields(), merged), PqcEvaluator.nistQuantumSecurityLevel(merged));
    }

    /**
     * @param ran false when disabled or another node held the lock, which is a skip rather than an empty success
     * @param failed rows the rule set threw on, each stamped {@code EVALUATION-FAILED} at the current generation
     */
    public record SweepOutcome(boolean ran, boolean aborted, int evaluated, int written, int failed, int batches) {

        static SweepOutcome skipped() {
            return new SweepOutcome(false, false, 0, 0, 0, 0);
        }
    }
}
