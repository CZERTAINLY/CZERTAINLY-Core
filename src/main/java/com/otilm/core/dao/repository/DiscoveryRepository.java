package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.DiscoveryHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveryRepository extends SecurityFilterRepository<DiscoveryHistory, UUID> {

    Optional<DiscoveryHistory> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"triggers"})
    DiscoveryHistory findWithTriggersByUuid(UUID uuid);

    Optional<DiscoveryHistory> findByName(String name);

    @Query("SELECT DISTINCT connectorName FROM DiscoveryHistory ")
    List<String> findDistinctConnectorName();

    @Modifying
    @Query("UPDATE DiscoveryHistory d SET d.message = :message, d.updated = CURRENT_TIMESTAMP WHERE d.uuid = :uuid")
    void updateMessage(@Param("uuid") UUID uuid, @Param("message") String message);
}
