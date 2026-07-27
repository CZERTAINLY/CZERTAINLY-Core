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
    Long countByDiscoveryAndNewlyDiscoveredAndProcessed(DiscoveryHistory history, boolean newlyDiscovered, boolean processed);
    Long countByDiscoveryAndProcessedErrorNotNull(DiscoveryHistory history);
    List<DiscoveryCertificate> findByCertificateContent(CertificateContent certificateContent);

    @Modifying
    @Query("UPDATE DiscoveryCertificate dc SET dc.processed = true, dc.processedError = :processedError, " +
            "dc.updated = CURRENT_TIMESTAMP WHERE dc.uuid = :uuid")
    void markProcessed(@Param("uuid") UUID uuid, @Param("processedError") String processedError);

    @Modifying
    @Query("UPDATE DiscoveryCertificate dc SET dc.processedError = :processedError, " +
            "dc.updated = CURRENT_TIMESTAMP WHERE dc.uuid = :uuid")
    void updateProcessedError(@Param("uuid") UUID uuid, @Param("processedError") String processedError);

}
