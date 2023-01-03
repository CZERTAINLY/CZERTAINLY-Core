package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.SignDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.VerifyDataRequestDto;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class CryptographicOperationServiceTest extends BaseSpringBootTest {

    @Autowired
    private CryptographicOperationService cryptographicOperationService;

    @Autowired
    private TokenInstanceService tokenInstanceService;

    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private FunctionGroupRepository functionGroupRepository;

    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    private TokenInstanceReference tokenInstanceReference;
    private TokenProfile tokenProfile;
    private Connector connector;
    private CryptographicKey key;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("tokenInstanceConnector");
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.CRYPTOGRAPHY_PROVIDER);
        functionGroup.setName(FunctionGroupCode.CRYPTOGRAPHY_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setConnectorUuid(connector.getUuid());
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setFunctionGroupUuid(functionGroup.getUuid());
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("Soft")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setName("testInstance");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReference.setConnectorUuid(connector.getUuid());
        tokenInstanceReference.setKind("sample");
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReferenceRepository.save(tokenInstanceReference);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("profile1");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setDescription("sample description");
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("testInstance");
        tokenProfileRepository.save(tokenProfile);

        key = new CryptographicKey();
        key.setName("testKey1");
        key.setDescription("sampleDescription");
        key.setTokenProfile(tokenProfile);
        cryptographicKeyRepository.save(key);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListCipherAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.
                        urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/cipher/[^/]+/attributes"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.listCipherAttributes(key.getUuid(), CryptographicAlgorithm.RSA);
    }

    @Test
    public void testListCipherAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listCipherAttributes(
                        tokenInstanceReference.getUuid(),
                        CryptographicAlgorithm.RSA
                )
        );
    }

    @Test
    public void testListSignatureAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/signature/[^/]+/attributes"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.listSignatureAttributes(
                key.getUuid(),
                CryptographicAlgorithm.RSA
        );
    }

    @Test
    public void testListSignatureAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listSignatureAttributes(
                        tokenInstanceReference.getUuid(),
                        CryptographicAlgorithm.RSA
                )
        );
    }

    @Test
    public void testListRandomDataAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/random/attributes"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.listRandomAttributes(
                key.getUuid()
        );
    }

    @Test
    public void testListRandomDataAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listRandomAttributes(
                        tokenInstanceReference.getUuid()
                )
        );
    }

    @Test
    public void testEncrypt() throws ConnectorException, CryptographicOperationException {
        CipherRequestData data = new CipherRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        CipherDataRequestDto requestDto = new CipherDataRequestDto();
        requestDto.setCipherData(List.of(data));
        requestDto.setCipherAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/encrypt"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.encryptData(
                key.getUuid(),
                requestDto);
    }

    @Test
    public void testEncrypt_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.encryptData(
                        tokenInstanceReference.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testEncryptValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.encryptData(
                        key.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testDecrypt() throws ConnectorException, CryptographicOperationException {
        CipherRequestData data = new CipherRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        CipherDataRequestDto requestDto = new CipherDataRequestDto();
        requestDto.setCipherData(List.of(data));
        requestDto.setCipherAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/decrypt"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.decryptData(key.getUuid(), requestDto);
    }

    @Test
    public void testDecrypt_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.decryptData(
                        tokenInstanceReference.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testDecryptValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.decryptData(
                        key.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testSign() throws ConnectorException, CryptographicOperationException {
        SignatureRequestData data = new SignatureRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        SignDataRequestDto requestDto = new SignDataRequestDto();
        requestDto.setData(List.of(data));
        requestDto.setSignatureAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/sign"))
                .willReturn(WireMock.ok()));

        cryptographicOperationService.signData(key.getUuid(), requestDto);
    }

    @Test
    public void testSign_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.signData(
                        tokenInstanceReference.getUuid(),
                        new SignDataRequestDto()
                )
        );
    }

    @Test
    public void testSignValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.signData(
                        key.getUuid(),
                        new SignDataRequestDto()
                )
        );
    }

    @Test
    public void testVerify() throws ConnectorException, CryptographicOperationException {
        SignatureRequestData data = new SignatureRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        VerifyDataRequestDto requestDto = new VerifyDataRequestDto();
        requestDto.setData(List.of(data));
        requestDto.setSignatureAttributes(List.of());
        requestDto.setSignatures(List.of(data));

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/verify"))
                .willReturn(WireMock.ok()));

        cryptographicOperationService.verifyData(key.getUuid(), requestDto);
    }

    @Test
    public void testVerify_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.verifyData(
                        tokenInstanceReference.getUuid(),
                        new VerifyDataRequestDto()
                )
        );
    }

    @Test
    public void testVerifyValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.verifyData(
                        key.getUuid(),
                        new VerifyDataRequestDto()
                )
        );
    }
}
