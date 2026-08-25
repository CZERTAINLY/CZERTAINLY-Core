package com.otilm.core.service.writer.cbom;

import com.otilm.core.cbom.asset.CryptoPropertiesDigest;
import com.otilm.core.cbom.asset.JsonColumnText;
import com.otilm.core.cbom.asset.OccurrenceEvidenceCapper;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetSourceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactional writes against {@code crypto_asset_source}, each of which also re-derives the owning asset's
 * merge bookkeeping in the same transaction.
 *
 * <p>
 * The two are inseparable on purpose: {@code crypto_asset.merged_crypto_properties} is one source's retained payload,
 * so a source change that did not re-elect would leave the asset attributing its payload to a source that no longer
 * says that -- or, on removal, to a source that no longer exists.
 *
 * <p>
 * Both methods take the asset's row lock before touching a source row. {@code crypto_asset} and
 * {@code crypto_asset_source} reference each other, so a path that locked the source first would deadlock against a
 * concurrent ingest on the same pair; locking asset-then-source everywhere closes that cycle.
 */
@Service
public class CryptoAssetSourceWriter {

    private final CryptoAssetSourceRepository sourceRepository;
    private final CryptoAssetRepository assetRepository;

    public CryptoAssetSourceWriter(CryptoAssetSourceRepository sourceRepository,
            CryptoAssetRepository assetRepository) {
        this.sourceRepository = sourceRepository;
        this.assetRepository = assetRepository;
    }

    /**
     * Records what one CBOM says about one asset -- its payload verbatim, its occurrence evidence capped -- and
     * re-elects the asset's merged payload from the result.
     *
     * @param occurrences every occurrence the CBOM reported; the unclipped count is stored, so the gap against the
     * retained evidence is the visible record that capping happened
     */
    @Transactional
    public void upsertSource(UUID assetUuid, UUID cbomUuid, Map<String, Object> cryptoProperties,
            List<Map<String, Object>> occurrences, OffsetDateTime seenAt) {
        assetRepository.lockForSourceChange(assetUuid);
        CryptoPropertiesDigest digest = CryptoPropertiesDigest.of(cryptoProperties);
        sourceRepository
                .upsertSource(UUID.randomUUID(), assetUuid, cbomUuid, JsonColumnText.render(cryptoProperties),
                        digest.leafCount(), digest.hash(),
                        JsonColumnText.render(OccurrenceEvidenceCapper.cap(occurrences)),
                        occurrences == null ? 0 : occurrences.size(), seenAt);
        assetRepository.recomputeMergeFromSources(assetUuid);
    }

    /**
     * Removes one CBOM's contribution to one asset and re-elects the asset's merged payload from what is left.
     *
     * <p>
     * An asset left with no sources is retained, with {@code source_count} at zero and its payload cleared, rather than
     * deleted: retention is reversible by a later sweep and deletion is not, and the epic's re-sync semantics have not
     * ratified which one applies.
     *
     * @return 1 if a source row was removed, 0 if there was none
     */
    @Transactional
    public int detachCbom(UUID assetUuid, UUID cbomUuid) {
        assetRepository.lockForSourceChange(assetUuid);
        int removed = sourceRepository.deleteForAssetAndCbom(assetUuid, cbomUuid);
        assetRepository.recomputeMergeFromSources(assetUuid);
        return removed;
    }
}
