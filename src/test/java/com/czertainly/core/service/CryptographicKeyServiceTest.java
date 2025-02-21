package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.czertainly.api.model.client.cryptography.key.*;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.*;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

class CryptographicKeyServiceTest extends BaseSpringBootTest {

    private static final String KEY_NAME = "testKey1";

    @Autowired
    private CryptographicKeyService cryptographicKeyService;
    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private TokenProfileRepository tokenProfileRepository;
    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;
    @Autowired
    private OwnerAssociationRepository ownerAssociationRepository;

    private Group group;
    private Connector connector;
    private TokenInstanceReference tokenInstanceReference;
    private TokenProfile tokenProfile;
    private TokenProfile tokenProfile2;
    private CryptographicKey key;
    private CryptographicKey keyWithoutToken;
    private CryptographicKeyItem publicKeyItem;
    private CryptographicKeyItem privateKeyItem;
    private WireMockServer mockServer;

    @BeforeEach
    void setUp() {
        // Start Mock Server
        mockServer = new WireMockServer(0);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        // Create and Save Connector
        connector = new Connector();
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.saveAndFlush(connector); // Ensure immediate persistence

        // create and save group
        group = new Group();
        group.setName("TestGroup");
        group.setDescription("Desc");
        groupRepository.save(group);

        // Create and Save TokenInstanceReferences
        tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setName("Token");
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReferenceRepository.saveAndFlush(tokenInstanceReference);

        // Create and Save TokenProfiles
        tokenProfile = new TokenProfile();
        tokenProfile.setName("profile1");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setDescription("sample description");
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("testInstance");
        tokenProfileRepository.saveAndFlush(tokenProfile);

        tokenProfile2 = new TokenProfile();
        tokenProfile2.setName("profile2");
        tokenProfile2.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile2.setDescription("sample description2");
        tokenProfile2.setEnabled(true);
        tokenProfile2.setTokenInstanceName("testInstance");
        tokenProfileRepository.saveAndFlush(tokenProfile2);

        // Create and Save CryptographicKey
        key = new CryptographicKey();
        key.setName(KEY_NAME);
        key.setTokenProfile(tokenProfile);
        key.setTokenInstanceReference(tokenInstanceReference);
        key.setDescription("initial description");
        key = cryptographicKeyRepository.saveAndFlush(key);

        // Create and Save CryptographicKeyItem - Private Key
        privateKeyItem = new CryptographicKeyItem();
        privateKeyItem.setLength(1024);
        privateKeyItem.setKey(key);
        privateKeyItem.setKeyUuid(key.getUuid());
        privateKeyItem.setType(KeyType.PRIVATE_KEY);
        privateKeyItem.setKeyData("some/encrypted/data");
        privateKeyItem.setFormat(KeyFormat.PRKI);
        privateKeyItem.setState(KeyState.ACTIVE);
        privateKeyItem.setEnabled(true);
        privateKeyItem.setKeyAlgorithm(KeyAlgorithm.RSA);
        privateKeyItem = cryptographicKeyItemRepository.saveAndFlush(privateKeyItem);

        // Create and Save CryptographicKeyItem - Public Key
        publicKeyItem = new CryptographicKeyItem();
        publicKeyItem.setLength(1024);
        publicKeyItem.setKey(key);
        publicKeyItem.setKeyUuid(key.getUuid());
        publicKeyItem.setType(KeyType.PUBLIC_KEY);
        publicKeyItem.setKeyData("some/encrypted/data");
        publicKeyItem.setFormat(KeyFormat.SPKI);
        publicKeyItem.setState(KeyState.ACTIVE);
        publicKeyItem.setEnabled(true);
        publicKeyItem.setKeyAlgorithm(KeyAlgorithm.RSA);
        publicKeyItem = cryptographicKeyItemRepository.saveAndFlush(publicKeyItem);

        // Update KeyReferenceUUIDs and Resave Items
        privateKeyItem.setKeyReferenceUuid(privateKeyItem.getUuid());
        publicKeyItem.setKeyReferenceUuid(publicKeyItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(privateKeyItem);
        cryptographicKeyItemRepository.saveAndFlush(publicKeyItem);

        // Associate Items with Key and Resave Key
        Set<CryptographicKeyItem> items = new HashSet<>();
        items.add(publicKeyItem);
        items.add(privateKeyItem);
        key.setItems(items);
        cryptographicKeyRepository.saveAndFlush(key);

        // Create and Save CryptographicKey without token
        keyWithoutToken = new CryptographicKey();
        keyWithoutToken.setName("testKeyWithoutToken");
        keyWithoutToken.setDescription("testKeyWithoutToken");
        keyWithoutToken = cryptographicKeyRepository.saveAndFlush(keyWithoutToken);

        // Create and Save CryptographicKeyItem - Public Key
        CryptographicKeyItem pk = new CryptographicKeyItem();
        pk.setLength(1024);
        pk.setKey(keyWithoutToken);
        pk.setKeyUuid(keyWithoutToken.getUuid());
        pk.setType(KeyType.PUBLIC_KEY);
        pk.setKeyData("some/encrypted/data");
        pk.setFormat(KeyFormat.SPKI);
        pk.setState(KeyState.ACTIVE);
        pk.setEnabled(true);
        pk.setKeyAlgorithm(KeyAlgorithm.ECDSA);
        cryptographicKeyItemRepository.saveAndFlush(pk);

        // Ensure OwnerAssociation is created and associated
        OwnerAssociation ownerAssociation = new OwnerAssociation();
        ownerAssociation.setOwnerUuid(UUID.randomUUID()); // Set a proper UUID
        ownerAssociation.setOwnerUsername("ownerName");
        ownerAssociation.setResource(Resource.CRYPTOGRAPHIC_KEY);
        ownerAssociation.setObjectUuid(key.getUuid());
        ownerAssociation.setKey(key);
        ownerAssociationRepository.saveAndFlush(ownerAssociation);

        key.setOwner(ownerAssociation);
        cryptographicKeyRepository.saveAndFlush(key);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testGetKeyByUuid() throws NotFoundException {
        KeyDetailDto dto = cryptographicKeyService.getKey(
                SecuredUUID.fromUUID(key.getUuid())
        );
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(key.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(2, dto.getItems().size());
    }

    @Test
    void testGetKeyByUuid_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    void testAddKey() throws ConnectorException, AlreadyExistException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/pair/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/secret/attributes"))
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
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/secret/attributes/validate"))
                .willReturn(WireMock.ok()));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/pair"))
                .willReturn(WireMock.okJson("{\"privateKeyData\":{\"name\":\"privateKey\", \"uuid\":\"149db148-8c51-11ed-a1eb-0242ac120002\", \"keyData\":{\"type\":\"Private\", \"algorithm\":\"RSA\", \"format\":\"Raw\", \"value\":\"something\"}}, \"publicKeyData\":{\"name\":\"publicKey\", \"uuid\":\"149db148-8c51-11ed-a1eb-0242ac120003\",  \"keyData\":{\"type\":\"Private\", \"algorithm\":\"RSA\", \"format\":\"Raw\", \"value\":\"something\"}}}")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/secret"))
                .willReturn(WireMock.okJson("{\"name\":\"secretkeyitem\", \"uuid\":\"149db149-8c51-11ed-a1eb-0242ac120003\", \"keyData\":{\"type\":\"Secret\", \"algorithm\":\"RSA\", \"format\":\"Raw\", \"value\":\"something secret\"}}}")));

        KeyRequestDto request = new KeyRequestDto();
        request.setName("testKeyPairKey");
        request.setDescription("sampleDescription");
        request.setAttributes(List.of());
        request.setGroupUuids(List.of(group.getUuid().toString()));

        KeyDetailDto dto = cryptographicKeyService.createKey(
                tokenInstanceReference.getUuid(),
                tokenProfile.getSecuredParentUuid(),
                KeyRequestType.KEY_PAIR,
                request
        );
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertEquals(2, dto.getItems().size());
        Assertions.assertEquals(1, dto.getGroups().size());
        Assertions.assertEquals(group.getUuid().toString(), dto.getGroups().getFirst().getUuid());

        // create secret key type
        request.setName("testSecretKey");
        request.setGroupUuids(null);
        dto = cryptographicKeyService.createKey(
                tokenInstanceReference.getUuid(),
                tokenProfile.getSecuredParentUuid(),
                KeyRequestType.SECRET,
                request
        );

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertEquals(1, dto.getItems().size());
    }

    @Test
    void testAddKey_validationFail() {
        KeyRequestDto request = new KeyRequestDto();

        UUID tokenInstanceReferenceUuid = tokenInstanceReference.getUuid();
        SecuredParentUUID tokenProfileUuid = tokenProfile.getSecuredParentUuid();
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.createKey(
                        tokenInstanceReferenceUuid,
                        tokenProfileUuid,
                        KeyRequestType.KEY_PAIR,
                        request
                )
        );
    }

    @Test
    void testAddKey_alreadyExist() {
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
    void testDestroyKey() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+"))
                .willReturn(WireMock.ok()));

        privateKeyItem.setState(KeyState.DEACTIVATED);
        publicKeyItem.setState(KeyState.DEACTIVATED);
        cryptographicKeyItemRepository.save(privateKeyItem);
        cryptographicKeyItemRepository.save(publicKeyItem);

        cryptographicKeyService.destroyKey(
                key.getUuid(),
                List.of(
                        privateKeyItem.getUuid().toString(),
                        publicKeyItem.getUuid().toString()
                )
        );
        Assertions.assertEquals(
                KeyState.DESTROYED,
                cryptographicKeyService.getKeyItem(key.getSecuredUuid(), privateKeyItem.getUuid().toString()).getState()
        );
    }

    @Test
    void testDestroyKey_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")
                )
        );
    }

    @Test
    void testDestroyKey_validationError() {
        mockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+")));

        UUID keyUuid = key.getUuid();
        List<String> keyItemsUuids = List.of(privateKeyItem.getUuid().toString(), publicKeyItem.getUuid().toString());
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.destroyKey(keyUuid, keyItemsUuids)
        );
    }

    @Test
    void testDestroyKey_parentKeyObject() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+"))
                .willReturn(WireMock.ok()));

        privateKeyItem.setState(KeyState.DEACTIVATED);
        publicKeyItem.setState(KeyState.DEACTIVATED);
        cryptographicKeyItemRepository.save(privateKeyItem);
        cryptographicKeyItemRepository.save(publicKeyItem);

        cryptographicKeyService.destroyKey(List.of(key.getUuid().toString()));

        KeyItemDetailDto keyItemDetailDto = cryptographicKeyService.getKeyItem(key.getSecuredUuid(), privateKeyItem.getUuid().toString());
        Assertions.assertEquals(KeyState.DESTROYED, keyItemDetailDto.getState());
        Assertions.assertNull(keyItemDetailDto.getKeyData());
    }

    @Test
    void testCompromiseKey() throws ConnectorException {
        cryptographicKeyService.compromiseKey(
                key.getUuid(),
                new CompromiseKeyRequestDto(
                        KeyCompromiseReason.UNAUTHORIZED_DISCLOSURE,
                        List.of(
                                privateKeyItem.getUuid(),
                                publicKeyItem.getUuid()
                        )
                )
        );
        Assertions.assertEquals(
                KeyState.COMPROMISED,
                cryptographicKeyService.getKeyItem(key.getSecuredUuid(), privateKeyItem.getUuid().toString()).getState()
        );
        Assertions.assertEquals(
                KeyState.COMPROMISED,
                cryptographicKeyService.getKeyItem(key.getSecuredUuid(), publicKeyItem.getUuid().toString()).getState()
        );
    }

    @Test
    void testCompromisedKey_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> cryptographicKeyService.getKey(
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")
                )
        );
    }

    @Test
    void testCompromiseKey_validationError() throws ConnectorException {
        cryptographicKeyService.compromiseKey(
                key.getUuid(),
                new CompromiseKeyRequestDto(
                        KeyCompromiseReason.UNAUTHORIZED_MODIFICATION,
                        List.of(
                                privateKeyItem.getUuid(),
                                publicKeyItem.getUuid()
                        )
                )
        );

        UUID keyUuid = key.getUuid();
        List<UUID> keyItemsUuids = List.of(privateKeyItem.getUuid(), publicKeyItem.getUuid());
        CompromiseKeyRequestDto keyRequestDto = new CompromiseKeyRequestDto(KeyCompromiseReason.UNAUTHORIZED_MODIFICATION, keyItemsUuids);
        Assertions.assertThrows(
                ValidationException.class,
                () -> cryptographicKeyService.compromiseKey(
                        keyUuid,
                        keyRequestDto
                )
        );
    }

    @Test
    void testCompromisedKey_parentKeyObject() throws NotFoundException {
        cryptographicKeyService.compromiseKey(new BulkCompromiseKeyRequestDto(KeyCompromiseReason.UNAUTHORIZED_SUBSTITUTION, List.of(key.getUuid())));
        Assertions.assertEquals(KeyState.COMPROMISED, cryptographicKeyService.getKeyItem(key.getSecuredUuid(), privateKeyItem.getUuid().toString()).getState());
        Assertions.assertNotEquals(null, privateKeyItem.getKeyData());
        Assertions.assertEquals(KeyState.COMPROMISED, cryptographicKeyService.getKeyItem(key.getSecuredUuid(), publicKeyItem.getUuid().toString()).getState());
        Assertions.assertNotEquals(null, publicKeyItem.getKeyData());
    }

    @Test
    void testUpdateKeyUsage() throws ConnectorException {
        UpdateKeyUsageRequestDto request = new UpdateKeyUsageRequestDto();
        request.setUuids(List.of(privateKeyItem.getUuid()));
        request.setUsage(List.of(KeyUsage.DECRYPT));
        cryptographicKeyService.updateKeyUsages(key.getUuid(), request);
        Assertions.assertEquals(
                1,
                cryptographicKeyService.getKeyItem(key.getSecuredUuid(), privateKeyItem.getUuid().toString()).getUsage().size()
        );
    }

    @Test
    void testUpdateKey() throws NotFoundException, AttributeException {
        EditKeyRequestDto request = new EditKeyRequestDto();
        request.setName("updatedName");
        request.setDescription("updatedDescription");
        request.setTokenProfileUuid(tokenProfile2.getUuid().toString());
        request.setGroupUuids(List.of(group.getUuid().toString()));

        cryptographicKeyService.editKey(key.getSecuredUuid(), request);

        KeyDetailDto keyDetailDto = cryptographicKeyService.getKey(key.getSecuredUuid());
        Assertions.assertEquals(request.getName(), keyDetailDto.getName());
        Assertions.assertEquals(request.getDescription(), keyDetailDto.getDescription());
        Assertions.assertEquals(request.getTokenProfileUuid(), keyDetailDto.getTokenProfileUuid());
        Assertions.assertEquals(1, keyDetailDto.getGroups().size());
        Assertions.assertEquals(group.getUuid().toString(), keyDetailDto.getGroups().getFirst().getUuid());

        TokenInstanceReference tokenInstanceReference2 = new TokenInstanceReference();
        tokenInstanceReference2.setName("Token2");
        tokenInstanceReference2.setTokenInstanceUuid("2l");
        tokenInstanceReference2.setConnector(connector);
        tokenInstanceReferenceRepository.saveAndFlush(tokenInstanceReference2);

        tokenProfile2.setTokenInstanceReference(tokenInstanceReference2);
        tokenProfileRepository.saveAndFlush(tokenProfile2);

        request.setName("");
        SecuredUUID securedUuid = key.getSecuredUuid();
        Assertions.assertThrows(ValidationException.class, () -> cryptographicKeyService.editKey(securedUuid, request));

        EditKeyRequestDto requestEmpty = new EditKeyRequestDto();
        keyDetailDto = cryptographicKeyService.editKey(key.getSecuredUuid(), requestEmpty);
        Assertions.assertEquals("updatedName", keyDetailDto.getName());
    }

    @Test
    void testSync_allNewObject() throws ConnectorException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys"))
                .willReturn(WireMock.okJson("""
                        [
                            {
                                "name":"key1",
                                "uuid":"e7426f1e-8ccc-11ed-a1eb-0242ac120002",
                                "association":"",
                                "keyData":{
                                    "type":"Secret",
                                    "algorithm":"RSA",
                                    "format":"Raw",
                                    "value":{"value":"sampleKeyValue"},
                                    "length":1024
                                }
                            },
                        \t{
                                "name":"key2",
                                "uuid":"e7426f1e-8ccc-11ed-a1eb-0242ac120003",
                                "association":"",
                                "keyData":{
                                    "type":"Private",
                                    "algorithm":"RSA",
                                    "format":"Raw",
                                    "value":{"value":"sampleKeyValue"},
                                    "length":1024
                                }
                            },
                        \t{
                                "name":"key3",
                                "uuid":"e7426f1e-8ccc-11ed-a1eb-0242ac120004",
                                "association":"",
                                "keyData":{
                                    "type":"Public",
                                    "algorithm":"RSA",
                                    "format":"Raw",
                                    "value":{"value":"sampleKeyValue"},
                                    "length":1024
                                }
                            },
                        \t{
                                "name":"key4",
                                "uuid":"e7426f1e-8ccc-11ed-a1eb-0242ac120005",
                                "association":"sampleKeyPair",
                                "keyData":{
                                    "type":"Private",
                                    "algorithm":"RSA",
                                    "format":"Raw",
                                    "value":{"value":"sampleKeyValue"},
                                    "length":1024
                                }
                            },
                        \t{
                                "name":"keySPrivate4",
                                "uuid":"e7426f1e-8ccc-11ed-a1eb-0242ac120006",
                                "association":"sampleKeyPair",
                                "keyData":{
                                    "type":"Public",
                                    "algorithm":"RSA",
                                    "format":"Raw",
                                    "value":{"value":"sampleKeyValue"},
                                    "length":1024
                                }
                            }
                        ]"""
                ))
        );
        cryptographicKeyService.syncKeys(tokenInstanceReference.getSecuredParentUuid());

        Assertions.assertEquals(8, cryptographicKeyItemRepository.count());
    }

    @Test
    void testSync_existingObject() throws ConnectorException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys"))
                .willReturn(WireMock.okJson("[\n" +
                        "    {\n" +
                        "        \"name\":\"key1\",\n" +
                        "        \"uuid\":\"" + privateKeyItem.getUuid().toString() + "\",\n" +
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
                        "        \"uuid\":\"" + publicKeyItem.getUuid().toString() + "\",\n" +
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

        Assertions.assertEquals(6, cryptographicKeyItemRepository.count());
    }

    @Test
    void testEditKeyItem() throws NotFoundException {
        final String NEW_NAME = "new name";
        EditKeyItemDto request = new EditKeyItemDto();
        request.setName(NEW_NAME);
        cryptographicKeyService.editKeyItem(SecuredUUID.fromUUID(key.getUuid()), privateKeyItem.getUuid(), request);
        privateKeyItem = cryptographicKeyItemRepository.findByUuid(privateKeyItem.getUuid()).orElse(null);
        Assertions.assertNotNull(privateKeyItem);
        Assertions.assertEquals(NEW_NAME, privateKeyItem.getName());
    }

    @Test
    void testListingKeys() {
        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setItemsPerPage(10);
        searchRequestDto.setPageNumber(1);
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CKI_CRYPTOGRAPHIC_ALGORITHM.toString(), FilterConditionOperator.EQUALS, "RSA")));
        CryptographicKeyResponseDto response = cryptographicKeyService.listCryptographicKeys(SecurityFilter.create(), searchRequestDto);
        Assertions.assertEquals(2, response.getTotalItems());

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CKI_TYPE.toString(), FilterConditionOperator.EQUALS, KeyType.PUBLIC_KEY.getCode())));
        response = cryptographicKeyService.listCryptographicKeys(SecurityFilter.create(), searchRequestDto);
        Assertions.assertEquals(2, response.getTotalItems());

        List<KeyDto> keyPairs = cryptographicKeyService.listKeyPairs(Optional.ofNullable(tokenProfile.getUuid().toString()), SecurityFilter.create());
        Assertions.assertEquals(1, keyPairs.size());

        publicKeyItem.setState(KeyState.DEACTIVATED);
        cryptographicKeyItemRepository.saveAndFlush(publicKeyItem);
        keyPairs = cryptographicKeyService.listKeyPairs(Optional.empty(), SecurityFilter.create());
        Assertions.assertEquals(0, keyPairs.size());
    }

    @Test
    void testDeleteKey() throws ConnectorException {
        cryptographicKeyService.deleteKey(key.getUuid(), List.of(publicKeyItem.getUuid().toString()));

        KeyDetailDto keyDetailDto = cryptographicKeyService.getKey(SecuredUUID.fromUUID(key.getUuid()));
        Assertions.assertEquals(1, keyDetailDto.getItems().size());
        Assertions.assertEquals(privateKeyItem.getUuid().toString(), keyDetailDto.getItems().getFirst().getUuid());

        cryptographicKeyService.deleteKey(key.getUuid(), List.of());
        Assertions.assertThrows(NotFoundException.class, () -> cryptographicKeyService.getKey(SecuredUUID.fromUUID(key.getUuid())));
    }

    @Test
    void testEnableDisableKey() throws ConnectorException {
        cryptographicKeyService.disableKey(key.getUuid(), List.of());

        KeyDetailDto keyDetailDto = cryptographicKeyService.getKey(SecuredUUID.fromUUID(key.getUuid()));
        Assertions.assertEquals(2, keyDetailDto.getItems().size());
        for (KeyItemDetailDto keyItemDto : keyDetailDto.getItems()) {
            Assertions.assertFalse(keyItemDto.isEnabled());
        }

        cryptographicKeyService.enableKey(key.getUuid(), List.of(privateKeyItem.getUuid().toString()));
        keyDetailDto = cryptographicKeyService.getKey(SecuredUUID.fromUUID(key.getUuid()));
        for (KeyItemDetailDto keyItemDto : keyDetailDto.getItems()) {
            if (keyItemDto.getUuid().equals(privateKeyItem.getUuid().toString())) {
                Assertions.assertTrue(keyItemDto.isEnabled());
            } else {
                Assertions.assertFalse(keyItemDto.isEnabled());
            }
        }

        cryptographicKeyService.disableKeyItems(List.of(privateKeyItem.getUuid().toString()));
        cryptographicKeyService.enableKey(List.of(key.getUuid().toString()));
        keyDetailDto = cryptographicKeyService.getKey(SecuredUUID.fromUUID(key.getUuid()));
        Assertions.assertEquals(2, keyDetailDto.getItems().size());
        for (KeyItemDetailDto keyItemDto : keyDetailDto.getItems()) {
            Assertions.assertTrue(keyItemDto.isEnabled());
        }
    }

    @Test
    void testKeyWithoutTokenOperations() throws ConnectorException, AttributeException {
        KeyDetailDto keyDetailDto = cryptographicKeyService.getKey(keyWithoutToken.getSecuredUuid());
        Assertions.assertEquals(1, keyDetailDto.getItems().size());

        String keyItemUuid = keyDetailDto.getItems().getFirst().getUuid();
        cryptographicKeyService.getKeyItem(keyWithoutToken.getSecuredUuid(), keyItemUuid);

        // try different operations if null token and profile is handled
        cryptographicKeyService.editKey(keyWithoutToken.getSecuredUuid(), new EditKeyRequestDto());
        cryptographicKeyService.disableKey(keyWithoutToken.getUuid(), List.of(keyItemUuid));
        cryptographicKeyService.enableKey(keyWithoutToken.getUuid(), null);

        CompromiseKeyRequestDto compromiseKeyRequestDto = new CompromiseKeyRequestDto();
        compromiseKeyRequestDto.setReason(KeyCompromiseReason.UNAUTHORIZED_MODIFICATION);
        cryptographicKeyService.compromiseKey(keyWithoutToken.getUuid(), compromiseKeyRequestDto);
        KeyItemDetailDto keyItemDetailDto = cryptographicKeyService.getKeyItem(keyWithoutToken.getSecuredUuid(), keyItemUuid);
        Assertions.assertEquals(KeyState.COMPROMISED, keyItemDetailDto.getState());
        Assertions.assertEquals(KeyCompromiseReason.UNAUTHORIZED_MODIFICATION, keyItemDetailDto.getReason());

        cryptographicKeyService.destroyKey(keyWithoutToken.getUuid(), null);
        keyItemDetailDto = cryptographicKeyService.getKeyItem(keyWithoutToken.getSecuredUuid(), keyItemUuid);
        Assertions.assertEquals(KeyState.DESTROYED_COMPROMISED, keyItemDetailDto.getState());

        cryptographicKeyService.deleteKey(keyWithoutToken.getUuid(), List.of(keyItemUuid));
        Assertions.assertThrows(NotFoundException.class, () -> cryptographicKeyService.getKeyItem(keyWithoutToken.getSecuredUuid(), keyItemUuid));
    }
}
