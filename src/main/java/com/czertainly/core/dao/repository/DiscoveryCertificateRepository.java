package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscoveryCertificateRepository extends SecurityFilterRepository<DiscoveryCertificate, Long> {
    Optional<DiscoveryCertificate> findByUuid(UUID uuid);
    long deleteByDiscovery(DiscoveryHistory history);

    List<DiscoveryCertificate> findByDiscovery(DiscoveryHistory history, Pageable pagable);

    @EntityGraph(attributePaths = {"certificateContent"})
    List<DiscoveryCertificate> findByDiscoveryAndNewlyDiscovered(DiscoveryHistory history, boolean newlyDiscovered, Pageable pageable);

    long countByDiscovery(DiscoveryHistory history);

    long countByDiscoveryAndNewlyDiscovered(DiscoveryHistory history, boolean newlyDiscovered);
    long countByDiscoveryAndNewlyDiscoveredAndProcessed(DiscoveryHistory history, boolean newlyDiscovered, boolean processed);
    List<DiscoveryCertificate> findByCertificateContent(CertificateContent certificateContent);

}
