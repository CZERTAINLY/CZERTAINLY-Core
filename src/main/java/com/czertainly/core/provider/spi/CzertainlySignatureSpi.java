package com.czertainly.core.provider.spi;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.connector.cryptography.operations.*;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.provider.key.CzertainlyPublicKey;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;
import java.util.stream.Collectors;

public class CzertainlySignatureSpi extends SignatureSpi {

    private static final Logger log = LoggerFactory.getLogger(CzertainlySignatureSpi.class);
    private final CryptographicOperationsApiClient apiClient;
    private CzertainlyPrivateKey privateKey;

    private CzertainlyPublicKey publicKey;

    private boolean isSign;

    private boolean isVerify;

    private byte[] buffer;

    private byte[] verifyBuffer;

    public CzertainlySignatureSpi(CryptographicOperationsApiClient apiClient) {
        this.apiClient = apiClient;
    }

    protected void engineVerifyUpdate(byte[] bytes, int off, int len) {

    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        if (privateKey == null) {
            throw new InvalidKeyException("Private key is null");
        }
        if (!(privateKey instanceof CzertainlyPrivateKey)) {
            throw new InvalidKeyException("Private key must be CzertainlyPrivateKey. Cannot be" + privateKey.getClass().getName());
        }
        this.privateKey = (CzertainlyPrivateKey) privateKey;
        this.isSign = true;
        this.buffer = new byte[0];
    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        engineUpdate(new byte[]{b}, 0, 1);
    }

    @Override
    protected void engineUpdate(byte[] bytes, int off, int len) throws SignatureException {
        if (isSign) {
            this.buffer = bytes;
        } else if (isVerify) {
            this.verifyBuffer = bytes;
        } else {
            throw new SignatureException("Invalid Operation");
        }
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        try {
            SignDataRequestDto requestDto = new SignDataRequestDto();
            requestDto.setSignatureAttributes(privateKey.getSignatureAttributes());
            SignatureRequestData signatureRequestData = new SignatureRequestData();
            signatureRequestData.setData(buffer);
            requestDto.setData(List.of(signatureRequestData));

            SignDataResponseDto response = apiClient.signData(
                    privateKey.getConnectorDto(),
                    privateKey.getTokenInstanceUuid(),
                    privateKey.getKeyUuid(),
                    requestDto
            );

            return response.getSignatures().get(0).getData();

        } catch (ConnectorException e) {
            throw new SignatureException(e);
        }
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        if (publicKey == null) {
            throw new InvalidKeyException("Public key is null");
        }
        if (!(publicKey instanceof CzertainlyPublicKey)) {
            throw new InvalidKeyException("Public key must be CzertainlyPublicKey. Cannot be " + publicKey.getClass().getName());
        }
        this.publicKey = (CzertainlyPublicKey) publicKey;
        this.isVerify = true;
        this.verifyBuffer = new byte[0];
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
        try {
            VerifyDataRequestDto requestDto = new VerifyDataRequestDto();
            requestDto.setSignatureAttributes(privateKey.getSignatureAttributes());
            SignatureRequestData signatureRequest = new SignatureRequestData();
            signatureRequest.setData(verifyBuffer);
            requestDto.setSignatures(List.of(signatureRequest));

            SignatureRequestData signatureRequestData = new SignatureRequestData();
            signatureRequestData.setData(publicKey.getData());
            signatureRequestData.setData(verifyBuffer);
            requestDto.setSignatures(List.of(signatureRequestData));

            VerifyDataResponseDto response = apiClient.verifyData(
                    publicKey.getConnectorDto(),
                    publicKey.getTokenInstanceUuid(),
                    publicKey.getKeyUuid(),
                    requestDto
            );

            return response.getVerifications().get(0).isResult();

        } catch (ConnectorException e) {
            throw new SignatureException(e);
        }
    }

    @Override
    protected void engineSetParameter(String s, Object o) throws InvalidParameterException {

    }

    @Override
    protected Object engineGetParameter(String s) throws InvalidParameterException {
        return null;
    }

}
