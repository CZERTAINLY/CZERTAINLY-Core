package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.CertificateContent;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveryCertificateRepository extends SecurityFilterRepository<DiscoveryCertificate, Long> {
    Optional<DiscoveryCertificate> findByUuid(UUID uuid);

    Long deleteByDiscovery(Discovery history);

    List<DiscoveryCertificate> findByDiscovery(Discovery history, Pageable pagable);

    @EntityGraph(attributePaths = {"certificateContent"})
    List<DiscoveryCertificate> findByDiscoveryUuidAndNewlyDiscovered(UUID discoveryUuid, boolean newlyDiscovered,
            Pageable pageable);

    /**
     * The v2 processing claim: the run's newly-discovered rows that no batch has handled yet, oldest first so a run
     * chews through its backlog in a stable order rather than revisiting the same page.
     */
    @EntityGraph(attributePaths = {"certificateContent"})
    List<DiscoveryCertificate> findByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalseOrderByCreatedAsc(
            UUID discoveryUuid, Pageable pageable);

    long countByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalse(UUID discoveryUuid);

    /** Whether any row of the run recorded a reason it could not be imported — what makes a run end WARNING. */
    boolean existsByDiscoveryUuidAndProcessedErrorIsNotNull(UUID discoveryUuid);

    Long countByDiscovery(Discovery history);

    Long countByDiscoveryAndNewlyDiscovered(Discovery history, boolean newlyDiscovered);

    List<DiscoveryCertificate> findByCertificateContent(CertificateContent certificateContent);

    /**
     * Batched deliberately: every row of a content group shares one outcome and one reason, so a group collapses to a
     * single statement rather than one transaction per row.
     */
    @Modifying
    @Query("UPDATE DiscoveryCertificate dc SET dc.processed = true, dc.processedError = :processedError, "
            + "dc.updated = CURRENT_TIMESTAMP WHERE dc.uuid IN :uuids")
    void markProcessed(@Param("uuids") Collection<UUID> uuids, @Param("processedError") String processedError);

    @Modifying
    @Query("UPDATE DiscoveryCertificate dc SET dc.processedError = :processedError, "
            + "dc.updated = CURRENT_TIMESTAMP WHERE dc.uuid IN :uuids")
    void updateProcessedError(@Param("uuids") Collection<UUID> uuids, @Param("processedError") String processedError);

}
