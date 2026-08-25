package com.otilm.core.dao.repository.cbom;

import com.otilm.core.dao.entity.cbom.CbomTombstone;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Deleted CBOMs, remembered.
 *
 * <p>
 * A plain {@link JpaRepository}: a tombstone is platform bookkeeping, never a listed resource.
 */
@Repository
public interface CbomTombstoneRepository extends JpaRepository<CbomTombstone, UUID> {

    boolean existsBySerialNumberAndVersion(String serialNumber, int version);

    /**
     * Records that a CBOM was deleted.
     *
     * <p>
     * <b>Concurrency:</b> {@code DO NOTHING} on the tombstone's own uuid -- the deleted CBOM's uuid -- so a retried
     * deletion is a no-op rather than a constraint violation. The first record of a deletion is the true one; a second
     * attempt has nothing to add.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}cbom_tombstone (uuid, serial_number, version, deleted_at, deleted_by)
            VALUES (:uuid, :serialNumber, :version, :deletedAt, :deletedBy)
            ON CONFLICT (uuid) DO NOTHING
            """, nativeQuery = true)
    void recordTombstone(@Param("uuid") UUID uuid, @Param("serialNumber") String serialNumber,
            @Param("version") int version, @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("deletedBy") String deletedBy);
}
