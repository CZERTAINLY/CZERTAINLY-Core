package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.SignDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.VerifyDataRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.enums.KeyFormat;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    private TokenInstanceReference tokenInstanceReference;
    private TokenProfile tokenProfile;
    private CryptographicKeyItem content;
    private CryptographicKeyItem content1;
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
        key.setTokenInstanceReference(tokenInstanceReference);
        cryptographicKeyRepository.save(key);

        content = new CryptographicKeyItem();
        content.setLength(1024);
        content.setCryptographicKey(key);
        content.setCryptographicKeyUuid(key.getUuid());
        content.setType(KeyType.PRIVATE_KEY);
        content.setKeyData("some/encrypted/data");
        content.setFormat(KeyFormat.PRKI);
        content.setState(KeyState.ACTIVE);
        content.setEnabled(true);
        content.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        cryptographicKeyItemRepository.save(content);

        content1 = new CryptographicKeyItem();
        content1.setLength(1024);
        content1.setCryptographicKey(key);
        content1.setCryptographicKeyUuid(key.getUuid());
        content1.setType(KeyType.PUBLIC_KEY);
        content1.setKeyData("some/encrypted/data");
        content1.setFormat(KeyFormat.SPKI);
        content1.setState(KeyState.ACTIVE);
        content1.setEnabled(true);
        content1.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        cryptographicKeyItemRepository.save(content1);

        content.setKeyReferenceUuid(content.getUuid());
        content1.setKeyReferenceUuid(content1.getUuid());
        cryptographicKeyItemRepository.save(content);
        cryptographicKeyItemRepository.save(content1);

        Set<CryptographicKeyItem> items = new HashSet<>();
        items.add(content1);
        items.add(content);
        key.setItems(items);
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
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/cipher/attributes"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.listCipherAttributes(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                CryptographicAlgorithm.RSA);
    }

    @Test
    public void testListCipherAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listCipherAttributes(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        tokenInstanceReference.getUuid(),
                        tokenProfile.getUuid(),
                        CryptographicAlgorithm.RSA
                )
        );
    }

    @Test
    public void testListSignatureAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/signature/attributes"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.listSignatureAttributes(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                CryptographicAlgorithm.RSA
        );
    }

    @Test
    public void testListSignatureAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listSignatureAttributes(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        tokenInstanceReference.getUuid(),
                        tokenProfile.getUuid(),
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
                tokenInstanceReference.getSecuredParentUuid()
        );
    }

    @Test
    public void testListRandomDataAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listRandomAttributes(
                        tokenInstanceReference.getSecuredParentUuid()
                )
        );
    }

    @Test
    public void testEncrypt() throws ConnectorException {
        CipherRequestData data = new CipherRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        CipherDataRequestDto requestDto = new CipherDataRequestDto();
        requestDto.setCipherData(List.of(data));
        requestDto.setCipherAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/encrypt"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.encryptData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    public void testEncrypt_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.encryptData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        key.getUuid(),
                        key.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testEncryptValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.encryptData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        key.getUuid(),
                        content1.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testDecrypt() throws ConnectorException {
        CipherRequestData data = new CipherRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        CipherDataRequestDto requestDto = new CipherDataRequestDto();
        requestDto.setCipherData(List.of(data));
        requestDto.setCipherAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/decrypt"
                        )
                )
                .willReturn(WireMock.ok()));

        cryptographicOperationService.decryptData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    public void testDecrypt_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.decryptData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        tokenInstanceReference.getUuid(),
                        key.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testDecryptValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.decryptData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        key.getUuid(),
                        content1.getUuid(),
                        new CipherDataRequestDto()
                )
        );
    }

    @Test
    public void testSign_RSA() throws ConnectorException {
        SignatureRequestData data = new SignatureRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        SignDataRequestDto requestDto = new SignDataRequestDto();
        requestDto.setData(List.of(data));
        requestDto.setSignatureAttributes(List.of());

        RequestAttributeDto reqDto1 = new RequestAttributeDto();
        reqDto1.setName("data_rsaSigScheme");
        reqDto1.setContent(List.of(new StringAttributeContent("PSS")));

        RequestAttributeDto reqDto2 = new RequestAttributeDto();
        reqDto2.setName("data_sigDigest");
        reqDto2.setContent(List.of(new StringAttributeContent("SHA-256")));

        requestDto.setSignatureAttributes(List.of(reqDto1, reqDto2));

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/sign"))
                .willReturn(WireMock.ok()));

        cryptographicOperationService.signData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    public void testSign_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.signData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        tokenInstanceReference.getUuid(),
                        key.getUuid(),
                        new SignDataRequestDto()
                )
        );
    }

    @Test
    public void testSignValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.signData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        key.getUuid(),
                        content1.getUuid(),
                        new SignDataRequestDto()
                )
        );
    }

    @Test
    public void testVerify() throws ConnectorException {
        SignatureRequestData data = new SignatureRequestData();
        data.setIdentifier("identifier");
        data.setData("Hello World!".getBytes());

        VerifyDataRequestDto requestDto = new VerifyDataRequestDto();
        requestDto.setData(List.of(data));
        requestDto.setSignatureAttributes(List.of());
        requestDto.setSignatures(List.of(data));

        RequestAttributeDto reqDto1 = new RequestAttributeDto();
        reqDto1.setName("data_rsaSigScheme");
        reqDto1.setContent(List.of(new StringAttributeContent("PSS")));

        RequestAttributeDto reqDto2 = new RequestAttributeDto();
        reqDto2.setName("data_sigDigest");
        reqDto2.setContent(List.of(new StringAttributeContent("SHA-256")));

        requestDto.setSignatureAttributes(List.of(reqDto1, reqDto2));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/verify"))
                .willReturn(WireMock.ok()));

        cryptographicOperationService.verifyData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    public void testVerify_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.verifyData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        key.getUuid(),
                        key.getUuid(),
                        new VerifyDataRequestDto()
                )
        );
    }

    @Test
    public void testVerifyValidationError() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicOperationService.verifyData(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        key.getUuid(),
                        content1.getUuid(),
                        new VerifyDataRequestDto()
                )
        );
    }
}
