package com.czertainly.core.config;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto;
import com.czertainly.api.model.connector.cryptography.operations.SignDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto;
import com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData;
import com.czertainly.api.model.core.connector.ConnectorDto;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class CryptographyProviderCsrSigner implements ContentSigner {

    private final CryptographicOperationsApiClient apiClient;
    private final ConnectorDto connector;
    private final UUID keyItemUuid;
    private final UUID tokenInstanceUuid;
    private final List<RequestAttributeDto> signatureAttributes;

    private final ByteArrayOutputStream outputStream;

    public CryptographyProviderCsrSigner(CryptographicOperationsApiClient apiClient,
                                         ConnectorDto connector,
                                         UUID tokenInstanceUuid,
                                         UUID keyItemUuid,
                                         List<RequestAttributeDto> signatureAttributes) {
        this.connector = connector;
        this.keyItemUuid = keyItemUuid;
        this.tokenInstanceUuid = tokenInstanceUuid;
        this.signatureAttributes = signatureAttributes;
        this.apiClient = apiClient;

        this.outputStream = new ByteArrayOutputStream();
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption);
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public byte[] getSignature() {
        byte[] dataToSign = outputStream.toByteArray();

        SignatureRequestData data = new SignatureRequestData();
        data.setData(dataToSign);

        SignDataRequestDto dto = new SignDataRequestDto();
        dto.setSignatureAttributes(signatureAttributes);
        dto.setData(List.of(data));

        try {
            SignDataResponseDto response = apiClient.signData(connector, tokenInstanceUuid.toString(), keyItemUuid.toString(), dto);


            SignatureRequestData data1 = new SignatureRequestData();
            data1.setData(response.getSignatures().get(0).getData());

            VerifyDataRequestDto requestDto = new VerifyDataRequestDto();
            requestDto.setSignatures(List.of(data1));
            requestDto.setSignatureAttributes(dto.getSignatureAttributes());
            requestDto.setData(dto.getData());

            apiClient.verifyData(connector, tokenInstanceUuid.toString(), "4b313869-cd08-4aa7-b1ba-0b9c7c98de2c", requestDto);

            return response.getSignatures().get(0).getData();

        } catch (ConnectorException e) {
            e.printStackTrace();
            return null;
        }
    }
}
