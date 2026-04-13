package com.czertainly.core.service.tsa.signer;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CryptographicOperationService;
import com.czertainly.core.util.CryptographyUtil;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaticManagedKeySignerCreator implements SignerCreator {

    private final CryptographicOperationService cryptographicOperationService;

    public StaticManagedKeySignerCreator(CryptographicOperationService cryptographicOperationService) {
        this.cryptographicOperationService = cryptographicOperationService;
    }

    @Override
    public boolean supports(SigningSchemeModel signingScheme) {
        return signingScheme instanceof StaticKeyManagedSigning;
    }

    @Override
    public Signer create(SigningSchemeModel signingSchemeModel) throws TspException {
        StaticKeyManagedSigning signingScheme = (StaticKeyManagedSigning) signingSchemeModel;

        CryptographicKey key = signingScheme.certificate().getKey();
        if (key == null) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("No cryptographic key associated with certificate '%s'", signingScheme.certificate().getCommonName()),
                    "Signing key could not be found.");
        }

        CryptographicKeyItem privateKeyItem = key.getItems().stream()
                .filter(item -> item.getType() == KeyType.PRIVATE_KEY)
                .findFirst()
                .orElseThrow(() -> new TspException(TspFailureInfo.SYSTEM_FAILURE,
                        String.format("No private key item found for key '%s'", key.getUuid()),
                        "Signing key could not be found."));

        CryptographicKeyItem publicKeyItem = key.getItems().stream()
                .filter(item -> item.getType() == KeyType.PUBLIC_KEY)
                .findFirst()
                .orElseThrow(() -> new TspException(TspFailureInfo.SYSTEM_FAILURE,
                        String.format("No public key item found for key '%s'", key.getUuid()),
                        "Signing key could not be found."));

        List<RequestAttribute> requestAttributes = signingScheme.signingOperationAttributes();

        String algorithmName = CryptographyUtil.resolveSignatureAlgorithmName(
                privateKeyItem.getKeyAlgorithm(), publicKeyItem.getKeyData(), requestAttributes);
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.findByCode(algorithmName);

        return new CryptographicOperationServiceSigner(
                cryptographicOperationService,
                SecuredParentUUID.fromUUID(key.getTokenInstanceReferenceUuid()),
                SecuredUUID.fromUUID(key.getTokenProfileUuid()),
                key.getUuid(),
                privateKeyItem.getUuid(),
                requestAttributes,
                signatureAlgorithm
        );
    }
}
