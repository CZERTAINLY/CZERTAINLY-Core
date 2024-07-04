package com.czertainly.core.service.cmp.message;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.service.CryptographicKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CertificateKeyServiceImpl implements CertificateKeyService {

    private CryptographicOperationsApiClient cryptographicOperationsApiClient;

    @Autowired
    public void setCryptographicOperationsApiClient(CryptographicOperationsApiClient cryptographicOperationsApiClient) {
        this.cryptographicOperationsApiClient = cryptographicOperationsApiClient;
    }

    private CryptographicKeyService cryptographicKeyService;

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    @Override
    public CzertainlyProvider getProvider(String cmpProfileName) {
        return CzertainlyProvider.getInstance(cmpProfileName,
                true, cryptographicOperationsApiClient);
    }

    @Override
    public CzertainlyPrivateKey getPrivateKey(Certificate certificate) {
        CryptographicKey key = certificate.getKey();
        CryptographicKeyItem item = cryptographicKeyService.getKeyItemFromKey(key, KeyType.PRIVATE_KEY);
        TokenInstanceReference tokenInsReference = key.getTokenInstanceReference();
        return new CzertainlyPrivateKey(
                tokenInsReference.getTokenInstanceUuid(),
                item.getKeyReferenceUuid().toString(),
                tokenInsReference.getConnector().mapToDto(),
                item.getKeyAlgorithm().getLabel()
        );
    }

}
