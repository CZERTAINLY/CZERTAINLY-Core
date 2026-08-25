package com.otilm.core.model.cbom;

/**
 * Where a CBOM row stands in cryptographic-asset ingest, independent of the header sync that created the row.
 *
 * <p>
 * {@link #PENDING} is the default the migration backfills onto existing rows, which is correct: their assets have never
 * been ingested. {@link #FAILED} pairs with {@code cbom.asset_sync_error}, whose text is shaped by us -- never a driver
 * message.
 *
 * <p>
 * Core-local placeholder for interfaces#874. The constant names are the persisted values.
 */
public enum CbomAssetSyncState {
    PENDING,
    IN_PROGRESS,
    SYNCED,
    FAILED
}
