package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.DiscoveryItem;
import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * One page of everything the run staged, certificates included. The two stores are unioned rather than merged:
     * certificate bytes stay deduplicated in {@code certificate_content} and are never copied into a staging row, so
     * the certificate branch builds its payload at read time.
     *
     * <p>
     * The resource filter is applied inside each branch, before the union, so a single-resource query reads one table
     * and the other branch is pruned on a constant. The {@code newlyDiscovered} filter is applied to the certificate
     * branch <i>after</i> its numbering, so a row keeps the same synthesized number whether or not the caller filtered.
     *
     * <p>
     * {@code i_cre} is cast explicitly: {@code discovery_certificate} declares it {@code VARCHAR} — alone among the
     * audited tables, and no migration has ever converted it — so comparing or coalescing it with a timestamp fails at
     * plan time on a Flyway-built database. Tests build their schema from the entities, where the column is a
     * timestamp, and so cannot catch it.
     *
     * @param resource enum member name to restrict to — what both tables store — or null for every resource
     * @param newlyDiscovered tri-state: null means both
     */
    // Aliases are quoted because Postgres folds an unquoted one to lower case, and the projection binds by exact
    // column label. Ordering is positional for the same reason: on a UNION only the first branch's labels are in
    // scope, and a qualified reference to them is not.
    @Query(value = """
            SELECT i.uuid AS "uuid",
                   i.inventory_uuid AS "inventoryUuid",
                   i.sequence AS "sequence",
                   i.unique_ref AS "uniqueRef",
                   i.resource AS "resource",
                   i.discovered_at AS "discoveredAt",
                   i.payload #>> '{}' AS "payload",
                   i.newly_discovered AS "newlyDiscovered",
                   (i.processed_at IS NOT NULL) AS "processed",
                   i.processed_error AS "processedError",
                   i.meta #>> '{}' AS "meta"
              FROM {h-schema}discovery_item i
             WHERE i.discovery_uuid = :discoveryUuid
               AND (CAST(:resource AS VARCHAR) IS NULL OR i.resource = CAST(:resource AS VARCHAR))
               AND (CAST(:newlyDiscovered AS BOOLEAN) IS NULL
                    OR i.newly_discovered = CAST(:newlyDiscovered AS BOOLEAN))
            UNION ALL
            SELECT c.uuid, c.inventory_uuid, c.sequence, c.unique_ref, c.resource, c.discovered_at, c.payload,
                   c.newly_discovered, c.processed, c.processed_error, c.meta
              FROM (
                SELECT dc.uuid AS uuid,
                       cert.uuid AS inventory_uuid,
                       COALESCE(dc.sequence,
                                    ROW_NUMBER() OVER (ORDER BY dc.i_cre::timestamptz, dc.uuid)) AS sequence,
                       COALESCE(dc.unique_ref, cc.fingerprint) AS unique_ref,
                       'CERTIFICATE' AS resource,
                       COALESCE(dc.discovered_at, dc.i_cre::timestamptz) AS discovered_at,
                       jsonb_build_object('resource', 'certificates',
                                          'certificateData', cc.content) #>> '{}' AS payload,
                       dc.newly_discovered AS newly_discovered,
                       dc.processed AS processed,
                       dc.processed_error AS processed_error,
                       dc.meta #>> '{}' AS meta
                  FROM {h-schema}discovery_certificate dc
                  JOIN {h-schema}certificate_content cc ON cc.id = dc.certificate_content_id
                  LEFT JOIN {h-schema}certificate cert ON cert.certificate_content_id = cc.id
                 WHERE dc.discovery_uuid = :discoveryUuid
                   AND (CAST(:resource AS VARCHAR) IS NULL OR CAST(:resource AS VARCHAR) = 'CERTIFICATE')
              ) c
             WHERE (CAST(:newlyDiscovered AS BOOLEAN) IS NULL
                    OR c.newly_discovered = CAST(:newlyDiscovered AS BOOLEAN))
             ORDER BY 3 NULLS LAST, 6, 1
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DiscoveryItemRow> listItems(@Param("discoveryUuid") UUID discoveryUuid, @Param("resource") String resource,
            @Param("newlyDiscovered") Boolean newlyDiscovered, @Param("limit") int limit, @Param("offset") long offset);

    /** Two indexed counts summed — the union itself is never materialized to size a page. */
    @Query(value = """
            SELECT (
                SELECT COUNT(*) FROM {h-schema}discovery_item i
                 WHERE i.discovery_uuid = :discoveryUuid
                   AND (CAST(:resource AS VARCHAR) IS NULL OR i.resource = CAST(:resource AS VARCHAR))
                   AND (CAST(:newlyDiscovered AS BOOLEAN) IS NULL
                        OR i.newly_discovered = CAST(:newlyDiscovered AS BOOLEAN))
            ) + (
                SELECT COUNT(*) FROM {h-schema}discovery_certificate dc
                 WHERE dc.discovery_uuid = :discoveryUuid
                   AND (CAST(:resource AS VARCHAR) IS NULL OR CAST(:resource AS VARCHAR) = 'CERTIFICATE')
                   AND (CAST(:newlyDiscovered AS BOOLEAN) IS NULL
                        OR dc.newly_discovered = CAST(:newlyDiscovered AS BOOLEAN))
            )
            """, nativeQuery = true)
    long countItems(@Param("discoveryUuid") UUID discoveryUuid, @Param("resource") String resource,
            @Param("newlyDiscovered") Boolean newlyDiscovered);
}
