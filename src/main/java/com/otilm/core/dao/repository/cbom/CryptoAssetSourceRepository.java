package com.otilm.core.dao.repository.cbom;

import com.otilm.core.dao.entity.cbom.CryptoAssetSource;
import com.otilm.core.dao.repository.SecurityFilterRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Queries and guarded writes for the many-to-many link between a cryptographic asset and the CBOMs that report it.
 *
 * <p>
 * Every {@code @Modifying} statement here is called from {@code CryptoAssetSourceWriter} and from nowhere else.
 */
@Repository
public interface CryptoAssetSourceRepository extends SecurityFilterRepository<CryptoAssetSource, UUID> {

    Optional<CryptoAssetSource> findByAssetUuidAndCbomUuid(UUID assetUuid, UUID cbomUuid);

    List<CryptoAssetSource> findByAssetUuidOrderByFirstSeenAt(UUID assetUuid);

    boolean existsByCbomUuid(UUID cbomUuid);

    /**
     * The assets a CBOM contributes to -- the work list for detaching that CBOM before its row is deleted, since the
     * foreign key is {@code RESTRICT}.
     */
    @Query("SELECT DISTINCT s.assetUuid FROM CryptoAssetSource s WHERE s.cbomUuid = :cbomUuid ORDER BY s.assetUuid")
    List<UUID> findAssetUuidsByCbomUuid(@Param("cbomUuid") UUID cbomUuid);

    /**
     * Records what one CBOM says about one asset, or refreshes it.
     *
     * <p>
     * <b>Concurrency:</b> atomic on the unique {@code (asset_uuid, cbom_uuid)} -- a re-sync of an unchanged document
     * rewrites the row rather than creating a second one.
     *
     * <p>
     * The seen-at pair is merged with {@code LEAST}/{@code GREATEST} rather than assigned, so the row always holds a
     * real window. Keeping {@code first_seen_at} and overwriting {@code last_seen_at} would look equivalent only while
     * events arrive in order: a retry, a replayed document or two nodes ingesting the same CBOM can present an older
     * {@code seenAt} after a newer one, and a plain assignment would then store a {@code last_seen_at} that precedes
     * {@code first_seen_at}. {@code occurrence_count} is the unclipped count, so the gap against the retained
     * {@code evidence} array records that capping happened.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}crypto_asset_source (uuid, asset_uuid, cbom_uuid, original_crypto_properties,
                    properties_leaf_count, properties_hash, evidence, occurrence_count, first_seen_at, last_seen_at)
            VALUES (:uuid, :assetUuid, :cbomUuid, CAST(:properties AS jsonb), :leafCount, :propertiesHash,
                    CAST(:evidence AS jsonb), :occurrenceCount, :seenAt, :seenAt)
            ON CONFLICT (asset_uuid, cbom_uuid) DO UPDATE SET
                original_crypto_properties = EXCLUDED.original_crypto_properties,
                properties_leaf_count = EXCLUDED.properties_leaf_count,
                properties_hash = EXCLUDED.properties_hash,
                evidence = EXCLUDED.evidence,
                occurrence_count = EXCLUDED.occurrence_count,
                first_seen_at = LEAST(crypto_asset_source.first_seen_at, EXCLUDED.first_seen_at),
                last_seen_at = GREATEST(crypto_asset_source.last_seen_at, EXCLUDED.last_seen_at)
            """, nativeQuery = true)
    void upsertSource(@Param("uuid") UUID uuid, @Param("assetUuid") UUID assetUuid, @Param("cbomUuid") UUID cbomUuid,
            @Param("properties") String properties, @Param("leafCount") int leafCount,
            @Param("propertiesHash") String propertiesHash, @Param("evidence") String evidence,
            @Param("occurrenceCount") int occurrenceCount, @Param("seenAt") OffsetDateTime seenAt);

    @Modifying
    @Query("DELETE FROM CryptoAssetSource s WHERE s.assetUuid = :assetUuid AND s.cbomUuid = :cbomUuid")
    int deleteForAssetAndCbom(@Param("assetUuid") UUID assetUuid, @Param("cbomUuid") UUID cbomUuid);
}
