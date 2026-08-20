package com.otilm.core.dao.repository;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveryRepository extends SecurityFilterRepository<Discovery, UUID> {

    Optional<Discovery> findByUuid(UUID uuid);

    /**
     * Pessimistic-write variant of {@link #findByUuid} for read-modify-write transitions on the run row. Issues
     * {@code SELECT ... FOR UPDATE}; must be called inside an active transaction, otherwise the lock is released
     * immediately on query completion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Discovery d WHERE d.uuid = :uuid")
    Optional<Discovery> findWithLockByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"triggers"})
    Discovery findWithTriggersByUuid(UUID uuid);

    Optional<Discovery> findByName(String name);

    @Query("SELECT DISTINCT connectorName FROM Discovery ")
    List<String> findDistinctConnectorName();

    @Modifying
    @Query("UPDATE Discovery d SET d.message = :message, d.updated = CURRENT_TIMESTAMP WHERE d.uuid = :uuid")
    void updateMessage(@Param("uuid") UUID uuid, @Param("message") String message);

    /**
     * Uuids of v2 runs (interface association present) still in one of {@code statuses} whose agenda is empty and that
     * were created before {@code threshold} — the tick engine can never drive them again. No row locking: the caller
     * re-asserts the condition under a per-run pessimistic lock before acting.
     */
    @Query("SELECT d.uuid FROM Discovery d WHERE d.connectorInterfaceUuid IS NOT NULL AND d.status IN :statuses "
            + "AND d.created < :threshold "
            + "AND NOT EXISTS (SELECT w FROM DiscoveryWork w WHERE w.discoveryUuid = d.uuid)")
    List<UUID> findWorkLostRunUuids(@Param("statuses") Collection<DiscoveryStatus> statuses,
            @Param("threshold") OffsetDateTime threshold, Pageable pageable);

    /**
     * Uuids of v2 runs still in one of {@code statuses} that were stopped before {@code threshold} — stopped runs whose
     * resume window has expired. No row locking: see {@link #findWorkLostRunUuids}.
     */
    @Query("SELECT d.uuid FROM Discovery d WHERE d.connectorInterfaceUuid IS NOT NULL AND d.status IN :statuses "
            + "AND d.stoppedAt IS NOT NULL AND d.stoppedAt < :threshold")
    List<UUID> findExpiredStoppedRunUuids(@Param("statuses") Collection<DiscoveryStatus> statuses,
            @Param("threshold") OffsetDateTime threshold, Pageable pageable);
}
