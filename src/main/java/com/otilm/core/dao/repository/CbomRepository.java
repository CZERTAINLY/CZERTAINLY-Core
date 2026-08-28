package com.otilm.core.dao.repository;

import com.otilm.api.model.core.cbom.CbomAssetSyncState;
import com.otilm.core.dao.entity.Cbom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CbomRepository extends SecurityFilterRepository<Cbom, UUID> {

    @Query("""
            SELECT c2
            FROM Cbom c1
            JOIN Cbom c2
                ON c1.serialNumber = c2.serialNumber
            WHERE c1.uuid = :uuid
            ORDER BY c2.version DESC
            """)
    List<Cbom> findVersionsByUuid(@Param("uuid") UUID uuid);

    boolean existsBySerialNumberAndVersion(String serialNumber, int version);

    @Query("SELECT c.uuid FROM Cbom c WHERE c.uuid IN :uuids")
    Set<UUID> findExistingUuids(@Param("uuids") List<UUID> uuids);

    /**
     * Writes the cryptographic-asset ingest state of one CBOM row.
     *
     * <p>
     * {@code error} is stored verbatim, so every caller must hand in text it shaped itself: this column is
     * operator-visible, and a driver message would carry the failing row with it.
     *
     * <p>
     * {@code assetsSyncedAt} records when this record last <em>succeeded</em>, so a null {@code syncedAt} leaves the
     * stored value alone rather than clearing it. Assigning it unconditionally would make a re-ingest that is merely in
     * progress -- or one that failed -- report the CBOM as never synced, while the assets from the last successful run
     * are still in the inventory and still returned by every query over them.
     *
     * <p>
     * {@code clearAutomatically}/{@code flushAutomatically}: this is a bulk update, so it bypasses the persistence
     * context. Without them a caller holding a managed {@link Cbom} keeps reading the pre-update state, and a later
     * dirty flush rewrites every column from that stale snapshot -- silently reverting the state this statement just
     * wrote, with no error. The writer's contract invites joining an ambient transaction, which is exactly the
     * composition that breaks.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Cbom c
            SET c.assetSyncState = :state,
                c.assetSyncError = :error,
                c.assetsSyncedAt = COALESCE(:syncedAt, c.assetsSyncedAt)
            WHERE c.uuid = :uuid
            """)
    int updateAssetSyncState(@Param("uuid") UUID uuid, @Param("state") CbomAssetSyncState state,
            @Param("error") String error, @Param("syncedAt") OffsetDateTime syncedAt);
}
