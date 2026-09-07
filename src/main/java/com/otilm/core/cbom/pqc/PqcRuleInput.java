package com.otilm.core.cbom.pqc;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import java.util.List;

/**
 * Everything a rule may read, and nothing else. This is the security boundary: {@code pqc_evaluated_fields} reaches
 * clients verbatim and {@code IdentityKeyExposureFence} is lexical, so it cannot see into the map. No identity,
 * canonical or absorbed key enters here, so none can be recorded.
 *
 * <p>
 * {@code nistQuantumSecurityLevel} is absent on purpose -- producers disagree about it for one asset, so a rule able to
 * read it would let the last producer to sync decide the verdict.
 *
 * @param hybridComponents folded, and possibly sized ({@code ml-kem-768}) -- not a bare family spelling
 * @param materialSize {@code null} for most rows, which is why an unsized symmetric key cannot be called ready
 */
public record PqcRuleInput(CryptographicAssetType assetType, String algorithmFamily, Integer parameterSet, String curve,
        String mode, String padding, String variant, String name, List<String> hybridComponents, String materialType,
        Integer materialSize) {

    public PqcRuleInput {
        hybridComponents = hybridComponents == null ? List.of() : List.copyOf(hybridComponents);
    }

    public boolean isHybrid() {
        return !hybridComponents.isEmpty();
    }
}
