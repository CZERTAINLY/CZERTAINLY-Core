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
     * The v2 processing claim: the certificate contents a run still has work for, oldest first, one page at a time.
     *
     * <p>
     * Pages by <b>content</b> rather than by row because the import pipeline groups rows by content and acts once per
     * group. Paging rows would let one content's rows straddle a page boundary, and the group would then be imported by
     * two separate ticks, running its triggers, histories and validation twice.
     *
     * <p>
     * Ordered oldest-first, breaking ties on the content id. The tiebreak is not decoration: {@code i_cre} comes from
     * the JVM clock and a staging loop writes rows faster than it advances, so without it two groups stamped in the
     * same tick could swap places between pages.
     *
     * <p>
     * <b>Accounted for, not processed.</b> The pipeline deliberately leaves {@code processed = false} on a row it never
     * reached, writing only the reason (see {@code CertificateDiscoveredEventHandler.writeBookkeeping}), so claiming on
     * {@code processed} alone would hand those same unreachable rows back on every tick and the backlog would never
     * drain. A recorded reason is an outcome.
     */
    @Query("SELECT dc.certificateContentId FROM DiscoveryCertificate dc "
            + "WHERE dc.discoveryUuid = :discoveryUuid AND dc.newlyDiscovered = true AND dc.processed = false "
            + "AND dc.processedError IS NULL "
            + "GROUP BY dc.certificateContentId ORDER BY MIN(dc.created), dc.certificateContentId")
    List<Long> findPendingContentIds(@Param("discoveryUuid") UUID discoveryUuid, Pageable pageable);

    /** Every pending row of the given contents, so a claimed group is always whole. */
    @EntityGraph(attributePaths = {"certificateContent"})
    List<DiscoveryCertificate> findByDiscoveryUuidAndCertificateContentIdInAndNewlyDiscoveredTrueAndProcessedFalseAndProcessedErrorIsNull(
            UUID discoveryUuid, Collection<Long> certificateContentIds);

    long countByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalseAndProcessedErrorIsNull(UUID discoveryUuid);

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
