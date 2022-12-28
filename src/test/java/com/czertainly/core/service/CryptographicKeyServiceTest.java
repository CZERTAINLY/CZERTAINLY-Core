package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
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

    private TokenInstanceReference tokenInstanceReference;
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

        key = new CryptographicKey();
        key.setName(KEY_NAME);
        key.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        key.setTokenInstanceReference(tokenInstanceReference);
        key.setDescription("initial description");
        cryptographicKeyRepository.save(key);
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
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+"))
                .willReturn(WireMock.okJson("{}")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/status"))
                .willReturn(WireMock.okJson("{}")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/attributes/validate"))
                .willReturn(WireMock.ok()));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/create"))
                .willReturn(WireMock.okJson("{}")));

        KeyRequestDto request = new KeyRequestDto();
        request.setName("testRaProfile2");
        request.setDescription("sampleDescription");
        request.setTokenProfileAttributes(List.of());
        request.setCreateKeyAttributes(List.of());

        KeyDetailDto dto = cryptographicKeyService.createKey(
                tokenInstanceReference.getSecuredParentUuid(),
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
                        request
                )
        );
    }

    @Test
    public void testDestroyKey() throws ConnectorException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/destroy"))
                .willReturn(WireMock.ok()));

        cryptographicKeyService.destroyKey(
                tokenInstanceReference.getSecuredParentUuid(),
                key.getUuid().toString()
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
}
