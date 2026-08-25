package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.model.cbom.CbomAssetSyncState;
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
     */
    @Modifying
    @Query("""
            UPDATE Cbom c
            SET c.assetSyncState = :state, c.assetSyncError = :error, c.assetsSyncedAt = :syncedAt
            WHERE c.uuid = :uuid
            """)
    int updateAssetSyncState(@Param("uuid") UUID uuid, @Param("state") CbomAssetSyncState state,
            @Param("error") String error, @Param("syncedAt") OffsetDateTime syncedAt);
}
