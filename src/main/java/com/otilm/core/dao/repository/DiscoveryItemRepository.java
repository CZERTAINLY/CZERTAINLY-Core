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
     * @param payload the item's payload, pre-serialized to JSON text
     * @param meta serialized {@code MetadataAttribute} list, or {@code null}
     */
    // S107: native query binds one parameter per column.
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
