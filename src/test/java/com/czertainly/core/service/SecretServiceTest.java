package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.connector.secrets.SecretContentResponseDto;
import com.czertainly.api.model.connector.secrets.SecretResponseDto;
import com.czertainly.api.model.connector.secrets.content.BasicAuthSecretContent;
import com.czertainly.api.model.connector.secrets.content.KeyValueSecretContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.secret.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.SecretsUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.SerializationUtils;
import wiremock.com.fasterxml.jackson.databind.DeserializationFeature;
import wiremock.com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class SecretServiceTest extends BaseSpringBootTest {

    private static final String TEST_CUSTOM_ATTRIBUTE = "testCustomAttribute";

    private static final int AUTH_SERVICE_MOCK_PORT = 10000;
    @Autowired
    private Secret2SyncVaultProfileRepository secret2SyncVaultProfileRepository;

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:" + AUTH_SERVICE_MOCK_PORT);
    }

    @Autowired
    private SecretService secretService;
    @Autowired
    private SecretRepository secretRepository;
    @Autowired
    private SecretVersionRepository secretVersionRepository;
    @Autowired
    private VaultInstanceRepository vaultInstanceRepository;
    @Autowired
    private VaultProfileRepository vaultProfileRepository;
    @Autowired
    private AttributeService attributeService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private ConnectorRepository connectorRepository;

    private Secret secret;
    private VaultProfile vaultProfile;
    private VaultInstance vaultInstance;
    private WireMockServer mockServer;
    private Connector connector;

    @BeforeEach
    void setUp() throws AlreadyExistException, AttributeException, NoSuchAlgorithmException, JsonProcessingException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());
        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/secretProvider/secrets/basicAuth/attributes"))
                .willReturn(WireMock.okJson("[]")));
        SecretResponseDto secretResponseDto = new SecretResponseDto();
        secretResponseDto.setName("testSecret");
        secretResponseDto.setVersion("1.2");
        WireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/secretProvider/secrets"))
                .willReturn(WireMock.okJson(new ObjectMapper().writeValueAsString(secretResponseDto))));
        WireMock.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/secretProvider/secrets"))
                .willReturn(WireMock.okJson(new ObjectMapper().writeValueAsString(secretResponseDto))));
        WireMock.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/secretProvider/secrets"))
                .willReturn(WireMock.ok()));
        SecretContentResponseDto secretContentResponseDto = new SecretContentResponseDto();
        BasicAuthSecretContent basicAuthSecretContent = new BasicAuthSecretContent();
        basicAuthSecretContent.setPassword("testPassword");
        basicAuthSecretContent.setUsername("testUsername");
        secretContentResponseDto.setContent(basicAuthSecretContent);
        WireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/secretProvider/secrets/content"))
                .willReturn(WireMock.okJson(new ObjectMapper().writeValueAsString(secretContentResponseDto))));

        connector = new Connector();
        connector.setName("testConnector");
        connector.setUrl("http://localhost:" + mockServer.port());
        connectorRepository.save(connector);

        vaultInstance = new VaultInstance();
        vaultInstance.setName("testInstance");
        vaultInstance.setConnector(connector);
        vaultInstanceRepository.save(vaultInstance);

        vaultProfile = new VaultProfile();
        vaultProfile.setName("testProfile");
        vaultProfile.setVaultInstance(vaultInstance);
        vaultProfile.setVaultInstanceUuid(vaultInstance.getUuid());
        vaultProfileRepository.save(vaultProfile);

        secret = new Secret();
        secret.setName("testSecret");
        secret.setType(com.czertainly.api.model.connector.secrets.SecretType.BASIC_AUTH);
        secret.setState(SecretState.ACTIVE);
        secret.setSourceVaultProfile(vaultProfile);
        secret.setSourceVaultProfileUuid(vaultProfile.getUuid());


        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setVersion(1);
        secretVersion.setVaultInstance(vaultInstance);
        secretVersion.setVaultInstanceUuid(vaultInstance.getUuid());
        BasicAuthSecretContent secretContent = new BasicAuthSecretContent();
        secretContent.setPassword("testPassword");
        secretContent.setUsername("testUsername");
        secretVersion.setFingerprint(CertificateUtil.getThumbprint(SerializationUtils.serialize(secretContent)));
        secretVersionRepository.save(secretVersion);

        secret.setLatestVersion(secretVersion);
        secretRepository.save(secret);

        secretVersion.setSecretUuid(secret.getUuid());
        secretVersionRepository.save(secretVersion);

        CustomAttributeCreateRequestDto dto = new CustomAttributeCreateRequestDto();
        dto.setName(TEST_CUSTOM_ATTRIBUTE);
        dto.setLabel("Test Attribute");
        dto.setContentType(AttributeContentType.STRING);
        dto.setResources(List.of(Resource.SECRET));
        attributeService.createCustomAttribute(dto);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testCreateSecret() throws NotFoundException, AttributeException, AlreadyExistException, ConnectorException {
        SecretRequestDto request = new SecretRequestDto();
        request.setName(secret.getName());
        Assertions.assertThrows(AlreadyExistException.class, () -> secretService.createSecret(request, vaultInstance.getSecuredParentUuid(), vaultProfile.getSecuredUuid()));
        request.setName("newSecret");
        Assertions.assertThrows(NotFoundException.class, () -> secretService.createSecret(request, SecuredParentUUID.fromUUID(UUID.randomUUID()), vaultInstance.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> secretService.createSecret(request, vaultProfile.getSecuredParentUuid(), SecuredUUID.fromUUID(UUID.randomUUID())));

        request.setDescription("Test secret description");
        request.setSourceVaultProfileUuid(vaultProfile.getUuid());
        BasicAuthSecretContent secretContent = new BasicAuthSecretContent();
        secretContent.setPassword("testPassword2");
        secretContent.setUsername("testUsername2");
        request.setSecret(secretContent);
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setName(TEST_CUSTOM_ATTRIBUTE);
        attribute.setContent(List.of(new StringAttributeContentV3("ref", "data")));
        request.setCustomAttributes(List.of(attribute));

        SecretDetailDto secretDetailDto = secretService.createSecret(request, vaultProfile.getSecuredParentUuid(), vaultInstance.getSecuredUuid());
        Assertions.assertNotNull(secretDetailDto);
        Assertions.assertEquals(request.getName(), secretDetailDto.getName());
        Assertions.assertNotNull(secretDetailDto.getUuid());
        Assertions.assertEquals(request.getDescription(), secretDetailDto.getDescription());
        Assertions.assertEquals(com.czertainly.api.model.connector.secrets.SecretType.BASIC_AUTH, secretDetailDto.getType());
        Assertions.assertEquals(1, secretDetailDto.getVersion());

        Assertions.assertNotNull(secretDetailDto.getCustomAttributes());
        Assertions.assertEquals(1, secretDetailDto.getCustomAttributes().size());
        Assertions.assertEquals(attribute.getName(), secretDetailDto.getCustomAttributes().getFirst().getName());
        Assertions.assertEquals("data", ((List<AttributeContent>) secretDetailDto.getCustomAttributes().getFirst().getContent()).getFirst().getData());
    }

    @Test
    void testUpdateSecret() throws NotFoundException, AttributeException, ConnectorException {
        Assertions.assertThrows(NotFoundException.class, () -> secretService.updateSecret(UUID.randomUUID(), new SecretUpdateRequestDto()));
        SecretUpdateRequestDto request = new SecretUpdateRequestDto();
        request.setDescription("Test secret new description");
        BasicAuthSecretContent secretContent = new BasicAuthSecretContent();
        secretContent.setPassword("testPassword3");
        secretContent.setUsername("testUsername3");
        request.setSecret(secretContent);
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setName(TEST_CUSTOM_ATTRIBUTE);
        attribute.setContent(List.of(new StringAttributeContentV3("ref", "data2")));
        request.setCustomAttributes(List.of(attribute));
        SecretDetailDto secretDetailDto = secretService.updateSecret(secret.getUuid(), request);

        Assertions.assertEquals(request.getDescription(), secretDetailDto.getDescription());
        Assertions.assertNotNull(secretDetailDto.getCustomAttributes());
        Assertions.assertEquals(1, secretDetailDto.getCustomAttributes().size());
        Assertions.assertEquals(attribute.getName(), secretDetailDto.getCustomAttributes().getFirst().getName());
        Assertions.assertEquals("data2", ((List<AttributeContent>) secretDetailDto.getCustomAttributes().getFirst().getContent()).getFirst().getData());
        Assertions.assertEquals(2, secretDetailDto.getVersion());

        // Check that the version has not changed with the same content
        secretDetailDto = secretService.updateSecret(secret.getUuid(), request);
        Assertions.assertEquals(2, secretDetailDto.getVersion());

        request.setSecret(null);
        secretDetailDto = secretService.updateSecret(secret.getUuid(), request);
        Assertions.assertEquals(2, secretDetailDto.getVersion());
    }

    @Test
    void testDeleteSecret() throws NotFoundException, ConnectorException {
        secret.getLatestVersion().setSecret(null);
        secretVersionRepository.save(secret.getLatestVersion());
        secretService.deleteSecret(secret.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> secretService.deleteSecret(secret.getUuid()));
    }

    @Test
    void testEnableSecret() throws NotFoundException {
        secretService.enableSecret(secret.getUuid());
        Assertions.assertTrue(secretRepository.findById(secret.getUuid()).orElseThrow().isEnabled());
    }

    @Test
    void testDisableSecret() throws NotFoundException {
        secretService.disableSecret(secret.getUuid());
        Assertions.assertFalse(secretRepository.findById(secret.getUuid()).orElseThrow().isEnabled());
    }

    @Test
    void testGetSecretVersions() throws NotFoundException {
        List<SecretVersionDto> secretVersions = secretService.getSecretVersions(secret.getUuid());
        Assertions.assertEquals(1, secretVersions.size());
        Assertions.assertEquals(1, secretVersions.getFirst().getVersion());
    }

    @Test
    void testAddAndRemoveVaultProfileToSecret() throws NotFoundException, ConnectorException, AttributeException {
        UUID secretUuid = secret.getUuid();
        UUID sourceVaultProfileUuid = vaultProfile.getUuid();
        List<RequestAttribute> createSecretAttributes = List.of();
        Assertions.assertThrows(ValidationException.class, () -> secretService.addVaultProfileToSecret(secretUuid, sourceVaultProfileUuid, createSecretAttributes));

        VaultProfile newVaultProfile = new VaultProfile();
        newVaultProfile.setName("newVaultProfile");
        newVaultProfile.setVaultInstance(vaultInstance);
        vaultProfileRepository.save(newVaultProfile);

        Secret2SyncVaultProfile secret2SyncVaultProfile = new Secret2SyncVaultProfile();
        secret2SyncVaultProfile.setId(new Secret2SyncVaultProfileId(secretUuid, newVaultProfile.getUuid()));
        secret2SyncVaultProfile.setSecret(secret);
        secret2SyncVaultProfile.setVaultProfile(newVaultProfile);
        secret2SyncVaultProfileRepository.save(secret2SyncVaultProfile);

        UUID newVaultProfileUuid = newVaultProfile.getUuid();
        Assertions.assertThrows(ValidationException.class, () -> secretService.addVaultProfileToSecret(secretUuid, newVaultProfileUuid, createSecretAttributes));

        secret2SyncVaultProfileRepository.delete(secret2SyncVaultProfile);

        secretService.addVaultProfileToSecret(secretUuid, newVaultProfileUuid, createSecretAttributes);

        Secret reloadedSecret = secretRepository.findWithAssociationsByUuid(secretUuid).orElseThrow();
        Assertions.assertTrue(reloadedSecret.getSyncVaultProfiles().stream().anyMatch(s -> s.getVaultProfile().getUuid().equals(newVaultProfileUuid)));

        Assertions.assertThrows(ValidationException.class, () -> secretService.removeVaultProfileFromSecret(secretUuid, sourceVaultProfileUuid));
        secretService.removeVaultProfileFromSecret(secretUuid, newVaultProfileUuid);

        reloadedSecret = secretRepository.findWithAssociationsByUuid(secretUuid).orElseThrow();
        Assertions.assertFalse(reloadedSecret.getSyncVaultProfiles().contains(secret2SyncVaultProfile));
    }


    @Test
    void testGetSecretDetails() throws NotFoundException {
        SecretDetailDto secretDetailDto = secretService.getSecretDetails(secret.getUuid());
        Assertions.assertNotNull(secretDetailDto);
        Assertions.assertEquals(secret.getUuid().toString(), secretDetailDto.getUuid());
        Assertions.assertEquals(secret.getName(), secretDetailDto.getName());
        Assertions.assertEquals(secret.getDescription(), secretDetailDto.getDescription());
        Assertions.assertEquals(secret.getType(), secretDetailDto.getType());
        Assertions.assertEquals(secret.getState(), secretDetailDto.getState());
        Assertions.assertEquals(1, secretDetailDto.getVersion());
    }

    @Test
    @Transactional
    void updateSourceVaultProfile() throws NotFoundException, ConnectorException, AttributeException {
        SecretUpdateObjectsDto updateObjectsDto = new SecretUpdateObjectsDto();
        updateObjectsDto.setSourceVaultProfileUuid(vaultProfile.getUuid());
        Assertions.assertDoesNotThrow(() -> secretService.updateSecretObjects(secret.getUuid(), updateObjectsDto));

        VaultProfile newVaultProfile = new VaultProfile();
        newVaultProfile.setName("newVaultProfile2");
        newVaultProfile.setVaultInstance(vaultInstance);
        vaultProfileRepository.save(newVaultProfile);

        updateObjectsDto.setSourceVaultProfileUuid(newVaultProfile.getUuid());
        secretService.updateSecretObjects(secret.getUuid(), updateObjectsDto);
        Secret reloadedSecret = secretRepository.findByUuid(SecuredUUID.fromUUID(secret.getUuid())).orElseThrow();
        Assertions.assertEquals(newVaultProfile.getUuid(), reloadedSecret.getSourceVaultProfileUuid());
        Assertions.assertEquals(1, reloadedSecret.getLatestVersion().getVersion());

        VaultInstance newVaultInstance = new VaultInstance();
        newVaultInstance.setName("newVaultInstance");
        newVaultInstance.setConnector(connector);
        vaultInstanceRepository.save(newVaultInstance);

        VaultProfile newVaultProfile2 = new VaultProfile();
        newVaultProfile2.setName("newVaultProfile2");
        newVaultProfile2.setVaultInstance(newVaultInstance);
        vaultProfileRepository.save(newVaultProfile2);

        updateObjectsDto.setSourceVaultProfileUuid(newVaultProfile2.getUuid());
        secretService.updateSecretObjects(secret.getUuid(), updateObjectsDto);
        reloadedSecret = secretRepository.findById(secret.getUuid()).orElseThrow();
        Assertions.assertEquals(newVaultProfile2, reloadedSecret.getSourceVaultProfile());
        Assertions.assertEquals(2, reloadedSecret.getLatestVersion().getVersion());
    }

    @Test
    void testSetSecretGroups() throws NotFoundException, ConnectorException, AttributeException {
        Group group = new Group();
        group.setName("TestGroup");
        group.setDescription("Test group description");
        group.setEmail("mai@example.com");
        groupRepository.save(group);
        SecretUpdateObjectsDto updateObjectsDto = new SecretUpdateObjectsDto();
        updateObjectsDto.setGroupUuids(Set.of(group.getUuid()));
        secretService.updateSecretObjects(secret.getUuid(), updateObjectsDto);
        Secret reloadedSecret = secretRepository.findWithAssociationsByUuid(secret.getUuid()).orElseThrow();
        Assertions.assertEquals(1, reloadedSecret.getGroups().size());
        Assertions.assertTrue(reloadedSecret.getGroups().contains(group));
        Assertions.assertDoesNotThrow(() -> secretService.updateSecretObjects(secret.getUuid(), updateObjectsDto));
    }

    @Test
    void testSetSecretOwner() throws NotFoundException, ConnectorException, AttributeException {
        WireMockServer mockServerUpdateUser = new WireMockServer(AUTH_SERVICE_MOCK_PORT);
        mockServerUpdateUser.start();
        WireMock.configureFor("localhost", mockServerUpdateUser.port());
        mockServerUpdateUser.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/[^/]+")).willReturn(
                WireMock.okJson("{ \"username\": \"newOwner\"}")
        ));

        SecretUpdateObjectsDto updateObjectsDto = new SecretUpdateObjectsDto();
        updateObjectsDto.setOwnerUuid(String.valueOf(UUID.randomUUID()));
        secretService.updateSecretObjects(secret.getUuid(), updateObjectsDto);
        Secret reloadedSecret = secretRepository.findWithAssociationsByUuid(secret.getUuid()).orElseThrow();
        Assertions.assertEquals("newOwner", reloadedSecret.getOwner().getOwnerUsername());
        mockServerUpdateUser.stop();
    }

    @Test
    void testListSecrets() {
        SearchRequestDto searchRequest = new SearchRequestDto();
        searchRequest.setFilters(List.of(
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SECRET_NAME.name(), FilterConditionOperator.CONTAINS, secret.getName()),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SECRET_TYPE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(secret.getType().getCode())),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SECRET_STATE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(secret.getState().getCode())),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SECRET_ENABLED.name(), FilterConditionOperator.EQUALS, secret.isEnabled()),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SECRET_SOURCE_VAULT_PROFILE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(secret.getSourceVaultProfile().getName())),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SECRET_SYNC_VAULT_PROFILE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(secret.getSourceVaultProfile().getName()))
        ));
        PaginationResponseDto<SecretDto> secrets = secretService.listSecrets(searchRequest, SecurityFilter.create());
        Assertions.assertEquals(1, secrets.getTotalItems());
        Assertions.assertEquals(secret.getName(), secrets.getItems().getFirst().getName());
    }

}
