package com.otilm.core.service.writer.cbom;

import com.otilm.api.exception.ValidationError;
import com.otilm.api.exception.ValidationException;
import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.repository.cbom.CryptoAssetAliasRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactional writes against {@code crypto_asset_alias} -- the repair path for an accidental duplicate.
 *
 * <p>
 * An alias never reaches identity. The key is a function of ten typed columns, so recording, re-pointing or removing an
 * alias cannot change any row's key; it changes only which row a later ingest lands on. That is also why the repair is
 * exactly reversible: every source keeps its own payload, so removing the alias and re-ingesting re-splits the rows
 * from evidence that was never discarded.
 *
 * <p>
 * An alias is <b>refused</b> where a safety rule caused the split. A guarded row was kept separate on purpose -- a
 * refuted certificate digest, a bare common name facing a full subject DN, a refuted OID -- and merging it would assert
 * an identity the evidence refuted. Chains are refused too: an alias whose absorbed key is another alias's canonical
 * key, or whose canonical key is itself absorbed, has no single reversal.
 */
@Service
public class CryptoAssetAliasWriter {

    private final CryptoAssetAliasRepository aliasRepository;
    private final CryptoAssetRepository assetRepository;

    public CryptoAssetAliasWriter(CryptoAssetAliasRepository aliasRepository, CryptoAssetRepository assetRepository) {
        this.aliasRepository = aliasRepository;
        this.assetRepository = assetRepository;
    }

    /**
     * Records, or re-points, the decision that one asset key is really another.
     *
     * @throws ValidationException if either side was split by a safety rule, if the decision is self-referential, or if
     * it would form a chain
     */
    @Transactional
    public void record(String absorbedKey, String canonicalKey, String reason, String decidedBy) {
        requireDistinct(absorbedKey, canonicalKey);
        requireUnguarded(canonicalKey, "canonical");
        requireUnguarded(absorbedKey, "absorbed");
        requireNoChain(absorbedKey, canonicalKey);
        requireSomethingToDecide(absorbedKey);
        aliasRepository
                .upsertAlias(UUID.randomUUID(), absorbedKey, canonicalKey, reason, decidedBy, OffsetDateTime.now());
    }

    /**
     * Withdraws an alias. A later ingest of the absorbed asset creates its own row again, rebuilt from the per-source
     * payloads that were retained throughout.
     *
     * @return 1 if an alias was removed, 0 if there was none
     */
    @Transactional
    public int remove(String absorbedKey) {
        return aliasRepository.deleteByAbsorbedKey(absorbedKey);
    }

    private void requireDistinct(String absorbedKey, String canonicalKey) {
        if (absorbedKey == null || canonicalKey == null || absorbedKey.equals(canonicalKey)) {
            throw new ValidationException(
                    ValidationError.create("A cryptographic asset alias needs two distinct asset keys."));
        }
    }

    /**
     * A guarded row was deliberately kept separate, so it may be neither absorbed nor absorbed into. A row that is
     * absent carries no guard to check: an absorbed row is already gone when an alias is re-pointed, and the guard that
     * mattered was checked when the merge was first decided and the row was still there.
     */
    private void requireUnguarded(String identity, String side) {
        Optional<CryptoAsset> asset = assetRepository.findByIdentityKey(identity);
        if (asset.isPresent() && asset.get().getIdentityGuard() != null) {
            throw new ValidationException(ValidationError
                    .create("The {} cryptographic asset was kept separate by the {} safety rule, so it cannot be "
                            + "merged by an alias.", side, asset.get().getIdentityGuard().name()));
        }
    }

    /**
     * Refuses a decision that would chain. Both directions matter: absorbing a key that another alias already treats as
     * canonical strands that alias behind two hops, and pointing at a key that is itself absorbed does the same on the
     * other side. Either way the repair stops having one reversal, which is the property that makes it safe.
     */
    private void requireNoChain(String absorbedKey, String canonicalKey) {
        if (aliasRepository.existsByCanonicalKey(absorbedKey)) {
            throw new ValidationException(ValidationError
                    .create("Another alias already points at the absorbed cryptographic asset; alias chains have no "
                            + "single reversal."));
        }
        if (aliasRepository.resolveCanonicalKey(canonicalKey).isPresent()) {
            throw new ValidationException(ValidationError
                    .create("The canonical cryptographic asset is itself absorbed by another alias; alias chains have "
                            + "no single reversal."));
        }
    }

    /**
     * Refuses a decision with nothing to decide about: neither an asset row to absorb nor an existing alias to
     * re-point. Without it a typo would record a redirect no ingest can ever follow.
     */
    private void requireSomethingToDecide(String absorbedKey) {
        if (aliasRepository.findByAbsorbedKey(absorbedKey).isEmpty()
                && assetRepository.findByIdentityKey(absorbedKey).isEmpty()) {
            throw new ValidationException(
                    ValidationError.create("There is no cryptographic asset to absorb, and no alias to re-point."));
        }
    }
}
