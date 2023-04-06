package com.czertainly.core.provider.spi;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.core.attribute.EncryptionAttributes;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;

public class CzertainlyCipherSpi extends CipherSpi {

    private final CryptographicOperationsApiClient apiClient;

    private static final Logger log = LoggerFactory.getLogger(CzertainlyCipherSpi.class);

    private CzertainlyPrivateKey privateKey;

    public CzertainlyCipherSpi(CryptographicOperationsApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    protected byte[] engineUpdate(byte[] b, int off, int len) {
        return null;
    }

    @Override
    protected int engineUpdate(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4) throws ShortBufferException {
        return 0;
    }

    @Override
    protected byte[] engineDoFinal(byte[] encryptedData, int arg1, int arg2) throws IllegalBlockSizeException, BadPaddingException {
        //TO call cryptography provider
        CipherDataRequestDto cipherDataRequestDto = new CipherDataRequestDto();
        CipherRequestData cipherRequestData = new CipherRequestData();
        cipherRequestData.setData(encryptedData);
        cipherDataRequestDto.setCipherAttributes(List.of(EncryptionAttributes.buildCmsRequestAttribute(true)));
        cipherDataRequestDto.setCipherData(List.of(cipherRequestData));

        try {
            apiClient.decryptData(
                    privateKey.getConnectorDto(),
                    privateKey.getTokenInstanceUuid(),
                    privateKey.getKeyUuid(),
                    cipherDataRequestDto
                    );
        } catch (ConnectorException e) {
            throw new RuntimeException(e);
        }

        return new byte[0];
    }

    @Override
    protected int engineDoFinal(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return 0;
    }

    @Override
    protected int engineGetBlockSize() {
        return 0;
    }

    @Override
    protected byte[] engineGetIV() {
        return null;
    }

    @Override
    protected int engineGetOutputSize(int arg0) {
        return 0;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        if (log.isDebugEnabled()) {
            log.debug("engineInit1: " + this.getClass().getName());
        }
        if (opmode != Cipher.DECRYPT_MODE && opmode != Cipher.UNWRAP_MODE) {
            throw new IllegalArgumentException("Only DECRYPT_MODE (2) or UNWRAP_MODE (4) can be used: " + opmode);
        }
        this.privateKey = (CzertainlyPrivateKey) key;
    }

    @Override
    protected void engineInit(int opmode, Key arg1, AlgorithmParameterSpec arg2, SecureRandom arg3)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
    }

    @Override
    protected void engineInit(int opmode, Key arg1, AlgorithmParameters arg2, SecureRandom arg3)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
    }

    @Override
    protected void engineSetPadding(String arg0) throws NoSuchPaddingException {
    }

}
