package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.Discovery;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
