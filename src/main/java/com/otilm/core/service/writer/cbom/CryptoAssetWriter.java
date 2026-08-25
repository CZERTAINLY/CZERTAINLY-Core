package com.otilm.core.service.writer.cbom;

import com.otilm.core.cbom.asset.CryptoAssetIdentityCalculator;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.cbom.asset.JsonColumnText;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.model.cbom.CryptoAssetIdentityGuard;
import com.otilm.core.model.cbom.PqcVerdict;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactional writes against {@code crypto_asset}. The repository carries no {@code @Transactional}; every
 * {@code @Modifying} write goes through this bean, and every method is {@code REQUIRED} so it joins the ingest
 * transaction it belongs to or opens its own.
 */
@Service
public class CryptoAssetWriter {

    private final CryptoAssetRepository assetRepository;

    public CryptoAssetWriter(CryptoAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /**
     * Keys the asset from its identity fields and inserts or refreshes the row under that key, stamping the rule-set
     * generation that produced it.
     *
     * <p>
     * The key is computed here rather than accepted from the caller, so a caller cannot store a row under a key that
     * does not describe it. Callers that must redirect an ingested asset onto another row -- the alias repair path --
     * resolve the alias and address the surviving asset by uuid instead; they never re-key it, because rewriting the
     * canonical row's identity columns with the absorbed row's fields would change what the canonical row claims to be.
     *
     * @return the uuid of the surviving row, which is the inserted uuid only when the insert won
     */
    @Transactional
    public UUID upsertIdentity(CryptoAssetIdentityFields fields, CryptoAssetIdentityGuard guard) {
        String key = CryptoAssetIdentityCalculator.calculate(fields);
        assetRepository
                .upsertIdentity(UUID.randomUUID(), key, CryptoAssetIdentityCalculator.RULESET_VERSION,
                        fields.assetType() == null ? null : fields.assetType().name(), fields.name(), fields.oid(),
                        fields.algorithmFamily(), fields.primitive(), fields.parameterSet(), fields.curve(),
                        fields.mode(), fields.padding(), fields.variant(), guard == null ? null : guard.name());
        return assetRepository
                .findUuidByIdentityKey(key)
                // Deliberately says nothing about the key: this text can reach an operator.
                .orElseThrow(() -> new IllegalStateException(
                        "The cryptographic asset row disappeared between its upsert and its lookup"));
    }

    /**
     * Re-derives the asset's merged payload, provenance pointer and source count from the sources it currently has.
     * Call it in the same transaction as any change to those sources, so the two can never be observed apart.
     */
    @Transactional
    public void recomputeMerge(UUID assetUuid) {
        assetRepository.recomputeMergeFromSources(assetUuid);
    }

    /**
     * Stores a PQC verdict with the rule that produced it, the rule-set generation, and the fields the rule read. The
     * identity columns and the identity rule-set version are untouched: a verdict is not an identity.
     *
     * @param reason operator-facing text, which the caller must have shaped itself
     */
    @Transactional
    public void applyPqcVerdict(UUID assetUuid, PqcVerdict verdict, String ruleId, String reason, int rulesetVersion,
            Map<String, Object> evaluatedFields) {
        assetRepository
                .applyPqcVerdict(assetUuid, verdict == null ? null : verdict.name(), ruleId, reason, rulesetVersion,
                        JsonColumnText.render(evaluatedFields));
    }

    /**
     * Deletes an asset. Its source rows and aliases go with it by cascade; the CBOM rows they referenced do not.
     *
     * @return 1 if a row was deleted, 0 if it was already gone
     */
    @Transactional
    public int delete(UUID assetUuid) {
        return assetRepository.deleteAsset(assetUuid);
    }
}
