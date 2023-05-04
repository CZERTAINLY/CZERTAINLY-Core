package com.czertainly.core.provider;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.collection.DigestAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.connector.cryptography.operations.DecryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.api.model.core.cryptography.key.RsaPadding;
import com.czertainly.core.attribute.RsaEncryptionAttributes;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import java.util.List;

public class CzertainlyCipherService {

    private static final Logger log = LoggerFactory.getLogger(CzertainlyCipherService.class);
    private final CryptographicOperationsApiClient apiClient;
    private final List<RequestAttributeDto> cipherAttributes;
    private final String algorithm;

    public CzertainlyCipherService(CryptographicOperationsApiClient apiClient, String algorithm) {
        this.apiClient = apiClient;
        this.cipherAttributes = mapCipherAttributesFromCipherAlgorithm(algorithm);
        this.algorithm = algorithm;
    }

    public List<RequestAttributeDto> mapCipherAttributesFromCipherAlgorithm(String algorithm) {
        switch (algorithm) {
            case "RSA", "RSA/NONE/PKCS1Padding", "RSA/ECB/PKCS1Padding" -> {
                return List.of(
                        RsaEncryptionAttributes.buildPadding(RsaPadding.PKCS1_v1_5)
                );
            }
            case "RSA/NONE/OAEPWithSHA1AndMGF1Padding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding" -> {
                return List.of(
                        RsaEncryptionAttributes.buildPadding(RsaPadding.OAEP),
                        RsaEncryptionAttributes.buildOaepHash(DigestAlgorithm.SHA_1),
                        RsaEncryptionAttributes.buildOaepMgf(true)
                );
            }
            default -> throw new IllegalArgumentException("No cipher attributes mapped for algorithm: " + algorithm);
        }
    }

    public byte[] decrypt(byte[] encryptedData, CzertainlyPrivateKey privateKey) throws BadPaddingException {
        // Prepare request to be made to the connector
        CipherDataRequestDto cipherDataRequestDto = new CipherDataRequestDto();
        CipherRequestData cipherRequestData = new CipherRequestData();
        cipherRequestData.setData(encryptedData);
        cipherDataRequestDto.setCipherAttributes(cipherAttributes);
        cipherDataRequestDto.setCipherData(List.of(cipherRequestData));

        log.debug("Decrypting data on connector: {} with token instance: {} and key: {}",
                privateKey.getConnectorDto().getName(),
                privateKey.getTokenInstanceUuid(),
                privateKey.getKeyUuid());

        try {
            DecryptDataResponseDto responseDto = apiClient.decryptData(
                    privateKey.getConnectorDto(),
                    privateKey.getTokenInstanceUuid(),
                    privateKey.getKeyUuid(),
                    cipherDataRequestDto
            );
            return responseDto.getDecryptedData().get(0).getData();
        } catch (ConnectorException e) {
            throw new BadPaddingException("Failed to decrypt on connector: " + e.getMessage());
        }
    }

    public String getAlgorithm() {
        return algorithm;
    }

}
