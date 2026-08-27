package com.otilm.core.service.writer.cbom;

import com.otilm.api.model.core.cbom.CbomAssetSyncState;
import com.otilm.core.dao.repository.CbomRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactional writes to a CBOM row's cryptographic-asset ingest state.
 *
 * <p>
 * Kept separate from the header sync that created the row: header sync and asset ingest fail independently, and a CBOM
 * whose header is stored but whose assets could not be parsed must be visibly {@code FAILED} rather than absent -- a
 * promise that holds only if {@link #markFailed} is called from outside the transaction that failed. See its
 * documentation.
 */
@Service
public class CbomAssetSyncStateWriter {

    private final CbomRepository cbomRepository;

    public CbomAssetSyncStateWriter(CbomRepository cbomRepository) {
        this.cbomRepository = cbomRepository;
    }

    /** Claims the CBOM for an ingest attempt, clearing any error from the previous one. */
    @Transactional
    public int markInProgress(UUID cbomUuid) {
        return cbomRepository.updateAssetSyncState(cbomUuid, CbomAssetSyncState.IN_PROGRESS, null, null);
    }

    @Transactional
    public int markSynced(UUID cbomUuid, OffsetDateTime syncedAt) {
        return cbomRepository.updateAssetSyncState(cbomUuid, CbomAssetSyncState.SYNCED, null, syncedAt);
    }

    /**
     * Records a failed ingest attempt.
     *
     * <p>
     * Call it only after the ingest transaction has rolled back. Every method on this writer is {@code REQUIRED}, by
     * the rule that a writer joins the transaction it is handed, so calling this one from inside the failing
     * transaction enrolls the FAILED row in that rollback: the state is lost, and nothing reports that it was lost. The
     * class promise above -- that a failure is visibly FAILED rather than absent -- is a promise the caller keeps, not
     * one this bean can enforce. Owning that boundary is the ingest orchestrator's job.
     *
     * @param operatorSafeError text the caller shaped itself. It is stored verbatim and shown to an operator, so a
     * driver or framework message must never be passed here: a constraint violation's {@code DETAIL} line carries the
     * failing row, and for {@code crypto_asset} that row carries the identity key. Use
     * {@code CryptoAssetConstraintTranslator} to derive a safe sentence from a database failure.
     */
    @Transactional
    public int markFailed(UUID cbomUuid, String operatorSafeError) {
        return cbomRepository.updateAssetSyncState(cbomUuid, CbomAssetSyncState.FAILED, operatorSafeError, null);
    }
}
