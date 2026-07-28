package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.CertificateContent;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.entity.DiscoveryHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscoveryCertificateRepository extends SecurityFilterRepository<DiscoveryCertificate, Long> {
    Optional<DiscoveryCertificate> findByUuid(UUID uuid);
    Long deleteByDiscovery(DiscoveryHistory history);

    List<DiscoveryCertificate> findByDiscovery(DiscoveryHistory history, Pageable pagable);

    @EntityGraph(attributePaths = {"certificateContent"})
    List<DiscoveryCertificate> findByDiscoveryUuidAndNewlyDiscovered(UUID discoveryUuid, boolean newlyDiscovered, Pageable pageable);

    Long countByDiscovery(DiscoveryHistory history);

    Long countByDiscoveryAndNewlyDiscovered(DiscoveryHistory history, boolean newlyDiscovered);
    List<DiscoveryCertificate> findByCertificateContent(CertificateContent certificateContent);

    /**
     * Batched deliberately: every row of a content group shares one outcome and one reason, so a group collapses
     * to a single statement rather than one transaction per row.
     */
    @Modifying
    @Query("UPDATE DiscoveryCertificate dc SET dc.processed = true, dc.processedError = :processedError, " +
            "dc.updated = CURRENT_TIMESTAMP WHERE dc.uuid IN :uuids")
    void markProcessed(@Param("uuids") Collection<UUID> uuids, @Param("processedError") String processedError);

    @Modifying
    @Query("UPDATE DiscoveryCertificate dc SET dc.processedError = :processedError, " +
            "dc.updated = CURRENT_TIMESTAMP WHERE dc.uuid IN :uuids")
    void updateProcessedError(@Param("uuids") Collection<UUID> uuids, @Param("processedError") String processedError);

}
