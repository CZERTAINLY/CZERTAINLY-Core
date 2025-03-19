package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorEntityNotFoundException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.operations.*;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CryptographicOperationServiceTest extends BaseSpringBootTest {

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
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("tokenInstanceConnector");
        connector.setUrl("http://localhost:"+mockServer.port());
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
        content.setKey(key);
        content.setKeyUuid(key.getUuid());
        content.setType(KeyType.PRIVATE_KEY);
        content.setKeyData("some/encrypted/data");
        content.setFormat(KeyFormat.PRKI);
        content.setState(KeyState.ACTIVE);
        content.setEnabled(true);
        content.setKeyAlgorithm(KeyAlgorithm.RSA);
        content.setUsage(List.of(KeyUsage.SIGN, KeyUsage.ENCRYPT, KeyUsage.VERIFY, KeyUsage.DECRYPT));
        cryptographicKeyItemRepository.save(content);

        content1 = new CryptographicKeyItem();
        content1.setLength(1024);
        content1.setKey(key);
        content1.setKeyUuid(key.getUuid());
        content1.setType(KeyType.PUBLIC_KEY);
        content1.setKeyData("some/encrypted/data");
        content1.setFormat(KeyFormat.SPKI);
        content1.setState(KeyState.ACTIVE);
        content1.setEnabled(true);
        content1.setKeyAlgorithm(KeyAlgorithm.RSA);
        content1.setUsage(List.of(KeyUsage.SIGN, KeyUsage.ENCRYPT, KeyUsage.VERIFY, KeyUsage.DECRYPT));
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
    void testListCipherAttributes() throws ConnectorException, NotFoundException {
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
                KeyAlgorithm.RSA
        );
    }

    @Test
    void testListCipherAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listCipherAttributes(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        tokenInstanceReference.getUuid(),
                        tokenProfile.getUuid(),
                        KeyAlgorithm.RSA
                )
        );
    }

    @Test
    void testListSignatureAttributes() throws ConnectorException, NotFoundException {
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
                KeyAlgorithm.RSA
        );
    }

    @Test
    void testListSignatureAttributes_NotFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicOperationService.listSignatureAttributes(
                        tokenInstanceReference.getSecuredParentUuid(),
                        tokenProfile.getSecuredUuid(),
                        tokenInstanceReference.getUuid(),
                        tokenProfile.getUuid(),
                        KeyAlgorithm.RSA
                )
        );
    }

    @Test
    void testListRandomDataAttributes() throws ConnectorException, NotFoundException {
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
    void testListRandomDataAttributes_NotFound() {
        Assertions.assertThrows(
                ConnectorEntityNotFoundException.class,
                () -> cryptographicOperationService.listRandomAttributes(
                        tokenInstanceReference.getSecuredParentUuid()
                )
        );
    }

    @Test
    void testEncrypt() throws ConnectorException, NotFoundException {
        CipherRequestData data = new CipherRequestData();
        data.setIdentifier("identifier");
        data.setData(Base64.getEncoder().encodeToString("Hello World!".getBytes(StandardCharsets.UTF_8)));

        CipherDataRequestDto requestDto = new CipherDataRequestDto();
        requestDto.setCipherData(List.of(data));
        requestDto.setCipherAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/encrypt"
                        )
                )
                .willReturn(WireMock.okJson("{}")));

        cryptographicOperationService.encryptData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    void testEncrypt_NotFound() {
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
    void testEncryptValidationError() {
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
    void testDecrypt() throws ConnectorException, NotFoundException {
        CipherRequestData data = new CipherRequestData();
        data.setIdentifier("identifier");
        data.setData(Base64.getEncoder().encodeToString("Hello World!".getBytes(StandardCharsets.UTF_8)));

        CipherDataRequestDto requestDto = new CipherDataRequestDto();
        requestDto.setCipherData(List.of(data));
        requestDto.setCipherAttributes(List.of());

        mockServer.stubFor(WireMock
                .post(WireMock
                        .urlPathMatching(
                                "/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/decrypt"
                        )
                )
                .willReturn(WireMock.okJson("{}")));

        cryptographicOperationService.decryptData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    void testDecrypt_NotFound() {
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
    void testDecryptValidationError() {
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
    void testSign_RSA() throws ConnectorException, NotFoundException {
        SignatureRequestData data = new SignatureRequestData();
        data.setIdentifier("identifier");
        data.setData(Base64.getEncoder().encodeToString("Hello World!".getBytes(StandardCharsets.UTF_8)));

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
                .willReturn(WireMock.okJson("{}")));

        cryptographicOperationService.signData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    void testSign_NotFound() {
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
    void testSignValidationError() {
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
    void testVerify() throws ConnectorException, NotFoundException {
        SignatureRequestData data = new SignatureRequestData();
        data.setIdentifier("identifier");
        data.setData(Base64.getEncoder().encodeToString("Hello World!".getBytes(StandardCharsets.UTF_8)));

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
                .willReturn(WireMock.okJson("{}")));

        cryptographicOperationService.verifyData(
                tokenInstanceReference.getSecuredParentUuid(),
                tokenProfile.getSecuredUuid(),
                key.getUuid(),
                content1.getUuid(),
                requestDto
        );
    }

    @Test
    void testVerify_NotFound() {
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
    void testVerifyValidationError() {
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
