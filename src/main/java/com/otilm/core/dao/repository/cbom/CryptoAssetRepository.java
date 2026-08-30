package com.otilm.core.dao.repository.cbom;

import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.repository.SecurityFilterRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Queries and guarded writes for the deduplicated cryptographic asset inventory.
 *
 * <p>
 * Every {@code @Modifying} statement here is called from {@code CryptoAssetWriter} and from nowhere else: the
 * transactional boundary lives on the writer, never on this interface.
 */
@Repository
public interface CryptoAssetRepository extends SecurityFilterRepository<CryptoAsset, UUID> {

    Optional<CryptoAsset> findByIdentityKey(String identityKey);

    @Query("SELECT a.uuid FROM CryptoAsset a WHERE a.identityKey = :key")
    Optional<UUID> findUuidByIdentityKey(@Param("key") String key);

    /**
     * Assets keyed by a rule-set generation older than {@code version} -- the re-keying sweep's work list, and the
     * reason the rule-set version is recorded on the row instead of folded into the key.
     */
    @Query("SELECT a.uuid FROM CryptoAsset a WHERE a.rulesetVersion < :version ORDER BY a.uuid")
    List<UUID> findUuidsKeyedBefore(@Param("version") int version);

    /**
     * Inserts the asset for an identity key, or refreshes the identity columns of the row already keyed under it.
     *
     * <p>
     * <b>Concurrency:</b> atomic on the unique identity key -- a concurrent loser is a clean re-write, not a constraint
     * violation, which is what makes a re-sync idempotent without a read-then-write window to race in.
     *
     * <p>
     * <b>Rule-set version:</b> written on both branches, from the same calculation that produced the key. A row the
     * current sync does not touch keeps the older version, which is what leaves it findable by
     * {@link #findUuidsKeyedBefore(int)}.
     *
     * <p>
     * <b>Identity:</b> on conflict the passed {@code uuid} is discarded with the rest of the losing insert; the caller
     * resolves the surviving row's uuid by its key afterwards.
     *
     * <p>
     * <b>Identity guard:</b> an existing guard survives, because it is a safety refusal rather than a field. A guard
     * says this row was deliberately kept separate — a refuted certificate digest, a bare common name facing a full
     * subject DN — and {@code CryptoAssetAliasWriter} refuses an alias by reading the guard that is on the row now.
     * Assigning {@code EXCLUDED.identity_guard} would therefore let any later unguarded report of the same normalized
     * identity clear the refusal as a side effect of ordinary re-ingest, and the merge it was protecting against would
     * become available. Lifting a guard is an explicit reviewed decision, never a consequence of re-reading a document.
     *
     * <p>
     * The merge bookkeeping and the PQC verdict are deliberately untouched: they belong to the source that was just
     * ingested and to the rule set, not to the identity.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}crypto_asset (uuid, identity_key, ruleset_version, asset_type, name, oid,
                    algorithm_family, primitive, parameter_set, curve, mode, padding, variant, identity_guard,
                    properties_leaf_count, source_count, i_cre, i_upd)
            VALUES (:uuid, :key, :rulesetVersion, :assetType, :name, :oid, :algorithmFamily, :primitive,
                    :parameterSet, :curve, :mode, :padding, :variant, :identityGuard,
                    0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (identity_key) DO UPDATE SET
                ruleset_version = EXCLUDED.ruleset_version,
                asset_type = EXCLUDED.asset_type,
                name = EXCLUDED.name,
                oid = EXCLUDED.oid,
                algorithm_family = EXCLUDED.algorithm_family,
                primitive = EXCLUDED.primitive,
                parameter_set = EXCLUDED.parameter_set,
                curve = EXCLUDED.curve,
                mode = EXCLUDED.mode,
                padding = EXCLUDED.padding,
                variant = EXCLUDED.variant,
                identity_guard = COALESCE(crypto_asset.identity_guard, EXCLUDED.identity_guard),
                i_upd = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertIdentity(@Param("uuid") UUID uuid, @Param("key") String key, @Param("rulesetVersion") int rulesetVersion,
            @Param("assetType") String assetType, @Param("name") String name, @Param("oid") String oid,
            @Param("algorithmFamily") String algorithmFamily, @Param("primitive") String primitive,
            @Param("parameterSet") String parameterSet, @Param("curve") String curve, @Param("mode") String mode,
            @Param("padding") String padding, @Param("variant") String variant,
            @Param("identityGuard") String identityGuard);

    /**
     * Takes the asset's row lock before its source rows are touched.
     *
     * <p>
     * {@code crypto_asset} and {@code crypto_asset_source} reference each other -- CASCADE one way, SET NULL the other
     * -- so a path that deleted a source first would lock source-then-asset while ingest locks asset-then-source, and
     * the two deadlock whenever they meet on the same {@code (asset, cbom)} pair. Every path takes this lock first, so
     * the cycle cannot close.
     *
     * <p>
     * {@code FOR NO KEY UPDATE} rather than {@code FOR UPDATE} (which is what {@code PESSIMISTIC_WRITE} would emit):
     * inserting a source row needs only {@code FOR KEY SHARE} on its parent, and {@code FOR UPDATE} would block that
     * unrelated traffic for no gain.
     */
    @Query(value = "SELECT a.uuid FROM {h-schema}crypto_asset a WHERE a.uuid = :uuid FOR NO KEY UPDATE",
            nativeQuery = true)
    Optional<UUID> lockForSourceChange(@Param("uuid") UUID uuid);

    /**
     * Re-derives the asset's merged payload, provenance pointer and source count from the sources it has right now.
     *
     * <p>
     * The election is a total order -- richest payload, then hash, then uuid -- so two nodes recomputing the same asset
     * reach the same answer and the pointer cannot oscillate. The count subquery is a plain {@code FROM} item rather
     * than a correlated {@code LEFT JOIN}, so the statement still updates the row when the asset has no sources left:
     * that is exactly the case where the pointer and payload must be cleared.
     */
    @Modifying
    @Query(value = """
            UPDATE {h-schema}crypto_asset a
            SET properties_source_uuid = elected.uuid,
                merged_crypto_properties = elected.original_crypto_properties,
                properties_leaf_count = COALESCE(elected.properties_leaf_count, 0),
                properties_hash = elected.properties_hash,
                source_count = counted.n,
                i_upd = CURRENT_TIMESTAMP
            FROM (SELECT count(*) AS n FROM {h-schema}crypto_asset_source s WHERE s.asset_uuid = :uuid) counted
            LEFT JOIN LATERAL (
                SELECT s.uuid, s.original_crypto_properties, s.properties_leaf_count, s.properties_hash
                FROM {h-schema}crypto_asset_source s
                WHERE s.asset_uuid = :uuid
                ORDER BY s.properties_leaf_count DESC, s.properties_hash ASC, s.uuid ASC
                LIMIT 1
            ) elected ON true
            WHERE a.uuid = :uuid
            """, nativeQuery = true)
    void recomputeMergeFromSources(@Param("uuid") UUID uuid);

    /**
     * Stores a PQC verdict together with the rule that produced it and the fields that rule read. The identity columns
     * and {@code ruleset_version} are untouched: a verdict is not an identity.
     */
    /**
     * <b>Decided versus evaluated:</b> the contract asks two different questions of a verdict, so the row answers both.
     * {@code pqc_evaluated_at} advances on every call, including one that re-confirms the verdict it already held;
     * {@code pqc_decided_at} moves only when the value actually changes, so it dates the finding rather than the last
     * time anybody looked. {@code IS DISTINCT FROM} rather than {@code <>} because either side may be null and a null
     * comparison would silently take the confirm branch. Neither is derivable from {@code i_upd}, which moves on every
     * identity refresh.
     */
    @Modifying
    @Query(value = """
            UPDATE {h-schema}crypto_asset
            SET pqc_verdict = :verdict,
                pqc_rule_id = :ruleId,
                pqc_reason = :reason,
                pqc_ruleset_version = :rulesetVersion,
                pqc_evaluated_fields = CAST(:evaluatedFields AS jsonb),
                pqc_evaluated_at = CURRENT_TIMESTAMP,
                pqc_decided_at = CASE
                    WHEN crypto_asset.pqc_verdict IS DISTINCT FROM CAST(:verdict AS TEXT) THEN CURRENT_TIMESTAMP
                    ELSE COALESCE(crypto_asset.pqc_decided_at, CURRENT_TIMESTAMP)
                END,
                i_upd = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
            """, nativeQuery = true)
    void applyPqcVerdict(@Param("uuid") UUID uuid, @Param("verdict") String verdict, @Param("ruleId") String ruleId,
            @Param("reason") String reason, @Param("rulesetVersion") int rulesetVersion,
            @Param("evaluatedFields") String evaluatedFields);

    @Modifying
    @Query("DELETE FROM CryptoAsset a WHERE a.uuid = :uuid")
    int deleteAsset(@Param("uuid") UUID uuid);
}
