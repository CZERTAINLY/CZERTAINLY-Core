package com.otilm.core.model.cbom;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** @param updated the row's {@code i_upd} as read; the guarded write refuses the row once it has moved */
public record PqcStaleVerdictRow(UUID uuid, CryptographicAssetType assetType, String name, String oid,
        String algorithmFamily, String primitive, String parameterSet, String curve, String mode, String padding,
        String variant, Map<String, Object> mergedCryptoProperties, OffsetDateTime updated) {

    public CryptoAssetIdentityFields fields() {
        return new CryptoAssetIdentityFields(assetType, name, oid, algorithmFamily, primitive, parameterSet, curve,
                mode, padding, variant);
    }
}
