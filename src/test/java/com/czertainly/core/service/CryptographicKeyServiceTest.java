package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.cryptography.key.*;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
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

public class CryptographicKeyServiceTest extends BaseSpringBootTest {

    private static final String KEY_NAME = "testKey1";

    @Autowired
    private CryptographicKeyService cryptographicKeyService;
    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private TokenProfileRepository tokenProfileRepository;
    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    private TokenInstanceReference tokenInstanceReference;
    private CryptographicKeyItem content;
    private CryptographicKeyItem content1;
    private TokenProfile tokenProfile;
    private Connector connector;
    private CryptographicKey key;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setUrl("http://localhost:"+mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReferenceRepository.save(tokenInstanceReference);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("profile1");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setDescription("sample description");
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("testInstance");
        tokenProfileRepository.save(tokenProfile);

        key = new CryptographicKey();
        key.setName(KEY_NAME);
        key.setTokenProfile(tokenProfile);
        key.setTokenInstanceReference(tokenInstanceReference);
        key.setDescription("initial description");
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
        content.setKeyAlgorithm(KeyAlgorithm.RSA);
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
        content1.setKeyAlgorithm(KeyAlgorithm.RSA);
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
    public void testGetKeyByUuid() throws NotFoundException {
        KeyDetailDto dto = cryptographicKeyService.getKey(
                tokenInstanceReference.getSecuredParentUuid(),
                SecuredUUID.fromUUID(key.getUuid())
        );
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(key.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(2, dto.getItems().size());
    }

    @Test
    public void testGetKeyByUuid_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    public void testAddKey() throws ConnectorException, AlreadyExistException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/pair/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+"))
                .willReturn(WireMock.okJson("{}")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/status"))
                .willReturn(WireMock.okJson("{}")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/pair/attributes/validate"))
                .willReturn(WireMock.ok()));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/pair"))
                .willReturn(WireMock.okJson("{\"privateKeyData\":{\"name\":\"privateKey\", \"uuid\":\"149db148-8c51-11ed-a1eb-0242ac120002\", \"keyData\":{\"type\":\"Private\", \"algorithm\":\"RSA\", \"format\":\"Raw\", \"value\":\"something\"}}, \"publicKeyData\":{\"name\":\"publicKey\", \"uuid\":\"149db148-8c51-11ed-a1eb-0242ac120003\",  \"keyData\":{\"type\":\"Private\", \"algorithm\":\"RSA\", \"format\":\"Raw\", \"value\":\"something\"}}}")));

        KeyRequestDto request = new KeyRequestDto();
        request.setName("testRaProfile2");
        request.setDescription("sampleDescription");
        request.setAttributes(List.of());

        KeyDetailDto dto = cryptographicKeyService.createKey(
                tokenInstanceReference.getUuid(),
                tokenProfile.getSecuredParentUuid(),
                KeyRequestType.KEY_PAIR,
                request
        );
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertEquals(2, dto.getItems().size());
    }

    @Test
    public void testAddKey_validationFail() {
        KeyRequestDto request = new KeyRequestDto();
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.createKey(
                        tokenInstanceReference.getUuid(),
                        tokenProfile.getSecuredParentUuid(),
                        KeyRequestType.KEY_PAIR,
                        request
                )
        );
    }

    @Test
    public void testAddKey_alreadyExist() {
        KeyRequestDto request = new KeyRequestDto();
        request.setName(KEY_NAME); // raProfile with same username exist

        Assertions.assertThrows(
                AlreadyExistException.class,
                () -> cryptographicKeyService.createKey(
                        tokenInstanceReference.getUuid(),
                        tokenProfile.getSecuredParentUuid(),
                        KeyRequestType.KEY_PAIR,
                        request
                )
        );
    }

    @Test
    public void testDestroyKey() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+"))
                .willReturn(WireMock.ok()));

        content.setState(KeyState.DEACTIVATED);
        content1.setState(KeyState.DEACTIVATED);
        cryptographicKeyItemRepository.save(content);
        cryptographicKeyItemRepository.save(content1);

        cryptographicKeyService.destroyKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid().toString(),
                List.of(
                        content.getUuid().toString(),
                        content1.getUuid().toString()
                )
        );
        Assertions.assertEquals(
                KeyState.DESTROYED,
                content.getState()
        );
    }

    @Test
    public void testDestroyKey_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")
                )
        );
    }

    @Test
    public void testDestroyKey_validationError() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+")));
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.destroyKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        key.getUuid().toString(),
                        List.of(
                                content.getUuid().toString(),
                                content1.getUuid().toString()
                        )
                )
        );
    }

    @Test
    public void testDestroyKey_parentKeyObject() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+"))
                .willReturn(WireMock.ok()));

        content.setState(KeyState.DEACTIVATED);
        content1.setState(KeyState.DEACTIVATED);
        cryptographicKeyItemRepository.save(content);
        cryptographicKeyItemRepository.save(content1);

        cryptographicKeyService.destroyKey(List.of(key.getUuid().toString()));
        Assertions.assertEquals(KeyState.DESTROYED, content.getState());
        Assertions.assertNull(content.getKeyData());
    }

    @Test
    public void testCompromiseKey() throws ConnectorException {
        cryptographicKeyService.compromiseKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid(),
                new CompromiseKeyRequestDto(
                        KeyCompromiseReason.UNAUTHORIZED_DISCLOSURE,
                        List.of(
                                content.getUuid(),
                                content1.getUuid()
                        )
                )
        );
        Assertions.assertEquals(
                KeyState.COMPROMISED,
                content.getState()
        );
    }

    @Test
    public void testCompromisedKey_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")
                )
        );
    }

    @Test
    public void testCompromiseKey_validationError() throws ConnectorException {
        cryptographicKeyService.compromiseKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid(),
                new CompromiseKeyRequestDto(
                        KeyCompromiseReason.UNAUTHORIZED_MODIFICATION,
                        List.of(
                                content.getUuid(),
                                content1.getUuid()
                        )
                )
        );
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.compromiseKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        key.getUuid(),
                        new CompromiseKeyRequestDto(
                                KeyCompromiseReason.UNAUTHORIZED_MODIFICATION,
                                List.of(
                                        content.getUuid(),
                                        content1.getUuid()
                                )
                        )
                )
        );
    }

    @Test
    public void testCompromisedKey_parentKeyObject() {
        cryptographicKeyService.compromiseKey(new BulkCompromiseKeyRequestDto(KeyCompromiseReason.UNAUTHORIZED_SUBSTITUTION, List.of(key.getUuid())));
        Assertions.assertEquals(KeyState.COMPROMISED, content.getState());
        Assertions.assertNotEquals(null, content.getKeyData());
        Assertions.assertEquals(KeyState.COMPROMISED, content1.getState());
        Assertions.assertNotEquals(null, content1.getKeyData());
    }

    @Test
    public void testUpdateKeyUsage() throws ConnectorException {
        UpdateKeyUsageRequestDto request = new UpdateKeyUsageRequestDto();
        request.setUuids(List.of(content.getUuid()));
        request.setUsage(List.of(KeyUsage.DECRYPT));
        cryptographicKeyService.updateKeyUsages(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid(),
                request
        );
        Assertions.assertEquals(
                1,
                content.getUsage().size()
        );
    }

    @Test
    public void testUpdateKey() throws NotFoundException, AttributeException {
        EditKeyRequestDto request = new EditKeyRequestDto();
        request.setName("updatedName");
        request.setDescription("updatedDescription");

        KeyDetailDto dto = cryptographicKeyService.editKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getSecuredUuid(),
                request
        );
        Assertions.assertEquals(request.getName(), key.getName());
        Assertions.assertEquals(request.getDescription(), key.getDescription());
    }

    @Test
    public void testSync_allNewObject() throws ConnectorException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys"))
                .willReturn(WireMock.okJson("[\n" +
                        "    {\n" +
                        "        \"name\":\"key1\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120002\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Secret\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key2\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120003\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Private\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key3\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120004\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Public\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120005\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Private\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"keySPrivate4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120006\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Public\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    }\n" +
                        "]"
                ))
        );
        cryptographicKeyService.syncKeys(tokenInstanceReference.getSecuredParentUuid());

        Assertions.assertEquals(7, cryptographicKeyItemRepository.count());
    }

    @Test
    public void testSync_existingObject() throws ConnectorException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys"))
                .willReturn(WireMock.okJson("[\n" +
                        "    {\n" +
                        "        \"name\":\"key1\",\n" +
                        "        \"uuid\":\"" + content.getUuid().toString() + "\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Secret\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key2\",\n" +
                        "        \"uuid\":\"" + content1.getUuid().toString() + "\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Private\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key3\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120004\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Public\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120005\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Private\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"keySPrivate4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120006\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"Public\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"Raw\",\n" +
                        "            \"value\":{\"value\":\"sampleKeyValue\"},\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    }\n" +
                        "]"
                ))
        );
        cryptographicKeyService.syncKeys(tokenInstanceReference.getSecuredParentUuid());

        Assertions.assertEquals(5, cryptographicKeyItemRepository.count());
    }
}
