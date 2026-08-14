package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.DiscoveryWork;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveryWorkRepository extends JpaRepository<DiscoveryWork, UUID> {

    /**
     * Returns rows due for a tick ({@code next_due_at} reached), soonest-due first. No row locking: cross-node mutual
     * exclusion is provided by the cluster-wide advisory lock held by the discovery sweep, so a plain ordered read is
     * enough.
     */
    List<DiscoveryWork> findByNextDueAtLessThanEqualOrderByNextDueAt(OffsetDateTime cutoff, Pageable pageable);

    /**
     * Inserts the pending row for a run and work type, or moves the existing row's due time. Atomic on the unique
     * {@code (discovery_uuid, work_type)} — unlike an exists-check-then-insert, a concurrent loser is a clean due-time
     * update (no constraint violation, no aborted transaction).
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}discovery_work (uuid, discovery_uuid, work_type, attempt, next_due_at)
            VALUES (:uuid, :discoveryUuid, :workType, 0, :nextDueAt)
            ON CONFLICT (discovery_uuid, work_type) DO UPDATE SET next_due_at = EXCLUDED.next_due_at
            """, nativeQuery = true)
    void schedule(@Param("uuid") UUID uuid, @Param("discoveryUuid") UUID discoveryUuid,
            @Param("workType") String workType, @Param("nextDueAt") OffsetDateTime nextDueAt);

    @Modifying
    @Query("UPDATE DiscoveryWork w SET w.attempt = :attempt, w.nextDueAt = :nextDueAt WHERE w.uuid = :uuid")
    void reschedule(@Param("uuid") UUID uuid, @Param("attempt") int attempt,
            @Param("nextDueAt") OffsetDateTime nextDueAt);

    @Modifying
    @Query("DELETE FROM DiscoveryWork w WHERE w.discoveryUuid = :discoveryUuid")
    void deleteByDiscoveryUuid(@Param("discoveryUuid") UUID discoveryUuid);
}
