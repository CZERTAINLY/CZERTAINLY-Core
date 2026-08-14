package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.DiscoveryWork;
import com.otilm.core.model.discovery.DiscoveryWorkType;
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
     * Inserts the pending row for a run and work type, or re-arms the existing row: due time moved, backoff counter
     * reset — scheduling is a fresh start, and in-flight backoff belongs to {@link #reschedule}. Atomic on the unique
     * {@code (discovery_uuid, work_type)} — unlike an exists-check-then-insert, a concurrent loser is a clean re-arm
     * (no constraint violation, no aborted transaction). On conflict the passed {@code uuid} is discarded with the rest
     * of the losing insert; rows are addressed by run and work type, never by that uuid.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}discovery_work (uuid, discovery_uuid, work_type, attempt, next_due_at)
            VALUES (:uuid, :discoveryUuid, :workType, 0, :nextDueAt)
            ON CONFLICT (discovery_uuid, work_type) DO UPDATE SET next_due_at = EXCLUDED.next_due_at, attempt = 0
            """, nativeQuery = true)
    void schedule(@Param("uuid") UUID uuid, @Param("discoveryUuid") UUID discoveryUuid,
            @Param("workType") String workType, @Param("nextDueAt") OffsetDateTime nextDueAt);

    // Addressed by the natural key, not the row uuid: schedule() discards the passed uuid on conflict, so a
    // caller-retained row uuid can silently address nothing — (run, work type) always addresses the live row.
    @Modifying
    @Query("UPDATE DiscoveryWork w SET w.attempt = :attempt, w.nextDueAt = :nextDueAt "
            + "WHERE w.discoveryUuid = :discoveryUuid AND w.workType = :workType")
    void reschedule(@Param("discoveryUuid") UUID discoveryUuid, @Param("workType") DiscoveryWorkType workType,
            @Param("attempt") int attempt, @Param("nextDueAt") OffsetDateTime nextDueAt);

    @Modifying
    @Query("DELETE FROM DiscoveryWork w WHERE w.discoveryUuid = :discoveryUuid")
    void deleteByDiscoveryUuid(@Param("discoveryUuid") UUID discoveryUuid);
}
