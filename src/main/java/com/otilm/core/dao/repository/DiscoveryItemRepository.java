package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.DiscoveryItem;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Staging store for every discovered resource except certificates, which keep their own v1 table.
 */
@Repository
public interface DiscoveryItemRepository extends JpaRepository<DiscoveryItem, UUID> {

    /**
     * Stages one drained item, ignoring a repeat of one already staged for the run.
     *
     * <p>
     * <b>Idempotency:</b> the connector's {@code uniqueRef} is the contract's dedupe key, so a redelivered page or an
     * overlapping cursor cannot double-stage. The conflict target is the whole natural key — the same {@code uniqueRef}
     * under a different resource is a different item.
     *
     * <p>
     * Native rather than an entity save because {@code ON CONFLICT DO NOTHING} has no JPA equivalent: a pre-read of the
     * existing refs would still miss duplicates within the page itself.
     *
     * @param payload the item's connector-reported union, already serialized — bound as text and cast, since the JDBC
     * driver has no jsonb binding of its own
     * @param meta serialized {@code MetadataAttribute} list, or {@code null}
     */
    // S107: a native query binds each column individually, so the parameter count is the column count. A
    // parameter object cannot be bound by @Param and would have to be unpacked at the only call site anyway.
    @SuppressWarnings("java:S107")
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}discovery_item
                (uuid, discovery_uuid, resource, sequence, unique_ref, payload, discovered_at, newly_discovered, meta)
            VALUES (:uuid, :discoveryUuid, :resource, :sequence, :uniqueRef, CAST(:payload AS jsonb),
                    :discoveredAt, :newlyDiscovered, CAST(:meta AS jsonb))
            ON CONFLICT (discovery_uuid, resource, unique_ref) DO NOTHING
            """, nativeQuery = true)
    void stage(@Param("uuid") UUID uuid, @Param("discoveryUuid") UUID discoveryUuid, @Param("resource") String resource,
            @Param("sequence") long sequence, @Param("uniqueRef") String uniqueRef, @Param("payload") String payload,
            @Param("discoveredAt") OffsetDateTime discoveredAt, @Param("newlyDiscovered") boolean newlyDiscovered,
            @Param("meta") String meta);
}
