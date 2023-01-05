package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.enums.KeyFormat;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

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
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
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
        content.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        cryptographicKeyItemRepository.save(content);

        content1 = new CryptographicKeyItem();
        content1.setLength(1024);
        content1.setCryptographicKey(key);
        content1.setCryptographicKeyUuid(key.getUuid());
        content1.setType(KeyType.PUBLIC_KEY);
        content1.setKeyData("some/encrypted/data");
        content1.setFormat(KeyFormat.SPKI);
        content1.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        cryptographicKeyItemRepository.save(content1);

        content.setKeyReferenceUuid(content.getUuid());
        content1.setKeyReferenceUuid(content1.getUuid());
        cryptographicKeyItemRepository.save(content);
        cryptographicKeyItemRepository.save(content1);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListKeys() {
        List<KeyDto> keys = cryptographicKeyService.listKeys(
                Optional.ofNullable(null),
                SecurityFilter.create()
        );
        Assertions.assertNotNull(keys);
        Assertions.assertFalse(keys.isEmpty());
        Assertions.assertEquals(1, keys.size());
        Assertions.assertEquals(key.getUuid().toString(), keys.get(0).getUuid());
    }

    @Test
    public void testGetKeyByUuid() throws NotFoundException {
        KeyDetailDto dto = cryptographicKeyService.getKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid().toString()
        );
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(key.getUuid().toString(), dto.getUuid());
    }

    @Test
    public void testGetKeyByUuid_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        "abfbc322-29e1-11ed-a261-0242ac120002")
        );
    }

    @Test
    public void testAddKey() throws ConnectorException, AlreadyExistException {
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
                .willReturn(WireMock.okJson("{\"privateKeyData\":{\"name\":\"privateKey\", \"uuid\":\"149db148-8c51-11ed-a1eb-0242ac120002\", \"keyData\":{}}, \"publicKeyData\":{\"name\":\"publicKey\", \"uuid\":\"149db148-8c51-11ed-a1eb-0242ac120003\", \"keyData\":{}}}")));

        KeyRequestDto request = new KeyRequestDto();
        request.setName("testRaProfile2");
        request.setDescription("sampleDescription");
        request.setTokenProfileUuid(tokenProfile.getUuid().toString());
        request.setAttributes(List.of());

        KeyDetailDto dto = cryptographicKeyService.createKey(
                tokenInstanceReference.getSecuredParentUuid(),
                KeyRequestType.KEY_PAIR,
                request
        );
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddKey_validationFail() {
        KeyRequestDto request = new KeyRequestDto();
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.createKey(
                        tokenInstanceReference.getSecuredParentUuid(),
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
                        tokenInstanceReference.getSecuredParentUuid(),
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

        cryptographicKeyService.destroyKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid().toString(),
                List.of(
                        content.getUuid().toString(),
                        content1.getUuid().toString()
                )
        );
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        key.getUuid().toString()
                )
        );
    }

    @Test
    public void testDestroyKey_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        tokenInstanceReference.getSecuredParentUuid(),
                        "abfbc322-29e1-11ed-a261-0242ac120002"
                )
        );
    }

    @Test
    public void testSync_allNewObject() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/list"))
                .willReturn(WireMock.okJson("[\n" +
                        "    {\n" +
                        "        \"name\":\"key1\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120002\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"SECRET_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key2\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120003\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PRIVATE_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key3\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120004\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PUBLIC_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120005\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PRIVATE_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"keySPrivate4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120006\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PUBLIC_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
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
    public void testSync_existingObject() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/list"))
                .willReturn(WireMock.okJson("[\n" +
                        "    {\n" +
                        "        \"name\":\"key1\",\n" +
                        "        \"uuid\":\"" + content.getUuid().toString() + "\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"SECRET_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key2\",\n" +
                        "        \"uuid\":\"" + content1.getUuid().toString() + "\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PRIVATE_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key3\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120004\",\n" +
                        "        \"association\":\"\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PUBLIC_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"key4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120005\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PRIVATE_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
                        "            \"length\":1024\n" +
                        "        }\n" +
                        "    },\n" +
                        "\t{\n" +
                        "        \"name\":\"keySPrivate4\",\n" +
                        "        \"uuid\":\"e7426f1e-8ccc-11ed-a1eb-0242ac120006\",\n" +
                        "        \"association\":\"sampleKeyPair\",\n" +
                        "        \"keyData\":{\n" +
                        "            \"type\":\"PUBLIC_KEY\",\n" +
                        "            \"algorithm\":\"RSA\",\n" +
                        "            \"format\":\"RAW\",\n" +
                        "            \"value\":\"sampleKeyValue\",\n" +
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
