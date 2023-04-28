package com.czertainly.core.provider.spi;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.connector.cryptography.operations.DecryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
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
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
        return 0;
    }

    @Override
    protected byte[] engineDoFinal(byte[] encryptedData, int inputOffset, int inputLen) throws IllegalBlockSizeException, BadPaddingException {
        //TO call cryptography provider
        CipherDataRequestDto cipherDataRequestDto = new CipherDataRequestDto();
        CipherRequestData cipherRequestData = new CipherRequestData();
        cipherRequestData.setData(encryptedData);
        cipherDataRequestDto.setCipherAttributes(privateKey.getCipherAttributes());
        cipherDataRequestDto.setCipherData(List.of(cipherRequestData));

        try {
            DecryptDataResponseDto responseDto = apiClient.decryptData(
                    privateKey.getConnectorDto(),
                    privateKey.getTokenInstanceUuid(),
                    privateKey.getKeyUuid(),
                    cipherDataRequestDto
                    );
            return responseDto.getDecryptedData().get(0).getData();
        } catch (ConnectorException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
        return 0;
    }

    @Override
    protected int engineGetBlockSize() {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
        return 0;
    }

    @Override
    protected byte[] engineGetIV() {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
        return null;
    }

    @Override
    protected int engineGetOutputSize(int value) {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
        return 0;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
        return null;
    }

    @Override
    protected void engineInit(int operationMode, Key key, SecureRandom random) throws InvalidKeyException {
        if (operationMode != Cipher.DECRYPT_MODE && operationMode != Cipher.UNWRAP_MODE) {
            throw new IllegalArgumentException("Unsupported Operation Mode: " + operationMode);
        }
        this.privateKey = (CzertainlyPrivateKey) key;
    }

    @Override
    protected void engineInit(int operationMode, Key key, AlgorithmParameterSpec arg2, SecureRandom arg3)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
    }

    @Override
    protected void engineInit(int operationMode, Key key, AlgorithmParameters arg2, SecureRandom arg3)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
    }

    @Override
    protected void engineSetMode(String operationMode) throws NoSuchAlgorithmException {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
    }

    @Override
    protected void engineSetPadding(String operationMode) throws NoSuchPaddingException {
        //Method is not implemented. The current implementation involves only for SCEP related items
        // And this method is not used.
        // This method should be implemented when the complete encryption and decryption is processed through the CZERTAINLY Provider
    }

}
