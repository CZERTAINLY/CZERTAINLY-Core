package com.otilm.core.dao.repository.cbom;

import com.otilm.core.dao.entity.cbom.CryptoAssetAlias;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The duplicate-repair alias table.
 *
 * <p>
 * Nothing on the keying path calls this interface. {@code CryptoAssetIdentityCalculator} takes ten typed fields and no
 * collaborator at all, so no alias can influence whether a key conforms. Aliases are resolved one layer up, at upsert
 * time, which is how they survive re-ingest without touching identity.
 *
 * <p>
 * A plain {@link JpaRepository} rather than a {@code SecurityFilterRepository}: aliases are an operator repair record,
 * never a listed, object-access-filtered resource.
 */
@Repository
public interface CryptoAssetAliasRepository extends JpaRepository<CryptoAssetAlias, UUID> {

    /**
     * Serializes alias decisions across the whole cluster for the remainder of the calling transaction.
     *
     * <p>
     * The no-chain and no-cycle rules are check-then-insert, and the table's only unique constraint is on
     * {@code absorbed_key}, which constrains each decision in isolation and says nothing about the pair. Two
     * transactions recording {@code A→B} and {@code B→C} therefore both read a chain-free table, both pass, and both
     * commit — leaving the chain the rules exist to forbid. No row is common to the two decisions, so there is nothing
     * to lock pessimistically; the lock has to be on the decision itself.
     *
     * <p>
     * Advisory rather than a lock table: it needs no row to exist, it is released by commit or rollback with no cleanup
     * path to get wrong, and alias repair is a rare operator action, so serializing all of it costs nothing worth
     * measuring. The lock is transaction-scoped, so a caller must already be in a transaction — which is why only
     * {@code CryptoAssetAliasWriter}'s {@code @Transactional} methods take it.
     *
     * @param key the advisory-lock key; every alias mutation must pass the same one
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS alias_decision_lock", nativeQuery = true)
    Integer lockAliasDecisions(@Param("key") long key);

    Optional<CryptoAssetAlias> findByAbsorbedKey(String absorbedKey);

    /** Whether any alias already redirects to this key -- the other half of the no-chains check. */
    boolean existsByCanonicalKey(String canonicalKey);

    /** The canonical key an absorbed key redirects to, if any. The upsert path's one lookup. */
    @Query("SELECT a.canonicalKey FROM CryptoAssetAlias a WHERE a.absorbedKey = :absorbedKey")
    Optional<String> resolveCanonicalKey(@Param("absorbedKey") String absorbedKey);

    /**
     * Records the decision that one key is really another, or re-points an existing decision at a new canonical key.
     *
     * <p>
     * <b>Concurrency:</b> atomic on the unique absorbed key, so two operators deciding at once produce one row, not a
     * constraint violation. Re-pointing keeps the row's identity and replaces the decision, which is what makes an
     * alias reversible: the per-source payloads it was derived from are still retained either way.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}crypto_asset_alias (uuid, absorbed_key, canonical_key, reason, decided_by,
                    decided_at)
            VALUES (:uuid, :absorbedKey, :canonicalKey, :reason, :decidedBy, :decidedAt)
            ON CONFLICT (absorbed_key) DO UPDATE SET
                canonical_key = EXCLUDED.canonical_key,
                reason = EXCLUDED.reason,
                decided_by = EXCLUDED.decided_by,
                decided_at = EXCLUDED.decided_at
            """, nativeQuery = true)
    void upsertAlias(@Param("uuid") UUID uuid, @Param("absorbedKey") String absorbedKey,
            @Param("canonicalKey") String canonicalKey, @Param("reason") String reason,
            @Param("decidedBy") String decidedBy, @Param("decidedAt") OffsetDateTime decidedAt);

    @Modifying
    @Query("DELETE FROM CryptoAssetAlias a WHERE a.absorbedKey = :absorbedKey")
    int deleteByAbsorbedKey(@Param("absorbedKey") String absorbedKey);
}
