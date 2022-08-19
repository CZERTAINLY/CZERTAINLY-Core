package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.attribute.AttributeCallback;
import com.czertainly.api.model.common.attribute.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.AttributeType;
import com.czertainly.api.model.common.attribute.AttributeValueTarget;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class CredentialServiceTest {

    private static final String CREDENTIAL_NAME = "testCredential1";

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    private Credential credential;
    private Connector connector;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("credentialProviderConnector");
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.CREDENTIAL_PROVIDER);
        functionGroup.setName(FunctionGroupCode.CREDENTIAL_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("ApiKey")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        credential = new Credential();
        credential.setKind("sample");
        credential.setName(CREDENTIAL_NAME);
        credential.setConnector(connector);
        credential = credentialRepository.save(credential);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListCredentials() {
        List<CredentialDto> credentials = credentialService.listCredentials();
        Assertions.assertNotNull(credentials);
        Assertions.assertFalse(credentials.isEmpty());
        Assertions.assertEquals(1, credentials.size());
        Assertions.assertEquals(credential.getUuid(), credentials.get(0).getUuid());
    }

    @Test
    public void testGetCredential() throws NotFoundException {
        CredentialDto dto = credentialService.getCredential(credential.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(credential.getUuid(), dto.getUuid());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(credential.getConnector().getUuid(), dto.getConnectorUuid());
    }

    @Test
    public void testGetCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.getCredential("wrong-uuid"));
    }

    @Test
    public void testAddCredential() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        CredentialRequestDto request = new CredentialRequestDto();
        request.setName("testCredential2");
        request.setConnectorUuid(connector.getUuid());
        request.setAttributes(List.of());
        request.setKind("ApiKey");

        CredentialDto dto = credentialService.createCredential(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(credential.getConnector().getUuid(), dto.getConnectorUuid());
    }

    @Test
    public void testAddCredential_validationFail() {
        CredentialRequestDto request = new CredentialRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> credentialService.createCredential(request));
    }

    @Test
    public void testAddCredential_alreadyExist() {
        CredentialRequestDto request = new CredentialRequestDto();
        request.setName(CREDENTIAL_NAME); // credential with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> credentialService.createCredential(request));
    }

    @Test
    public void testEditCredential() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        CredentialUpdateRequestDto request = new CredentialUpdateRequestDto();
        request.setAttributes(List.of());

        CredentialDto dto = credentialService.editCredential(credential.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(credential.getConnector().getUuid(), dto.getConnectorUuid());
    }

    @Test
    public void testEditCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.editCredential("wrong-uuid", null));
    }

    @Test
    public void testRemoveCredential() throws NotFoundException {
        credentialService.deleteCredential(credential.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.getCredential(credential.getUuid()));
    }

    @Test
    public void testRemoveCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.deleteCredential("wrong-uuid"));
    }

    @Test
    public void testEnableCredential() throws NotFoundException {
        credentialService.enableCredential(credential.getUuid());
        Assertions.assertEquals(true, credential.getEnabled());
    }

    @Test
    public void testEnableCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.enableCredential("wrong-uuid"));
    }

    @Test
    public void testDisableCredential() throws NotFoundException {
        credentialService.disableCredential(credential.getUuid());
        Assertions.assertEquals(false, credential.getEnabled());
    }

    @Test
    public void testDisableCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.disableCredential("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        credentialService.bulkDeleteCredential(List.of(credential.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.getCredential(credential.getUuid()));
    }

    @Test
    public void testLoadFullData_attributes() throws NotFoundException {
        HashMap<String, String> nameAndUuidMap = new HashMap<>();
        HashMap<String, Object> content = new HashMap<>();
        nameAndUuidMap.put("uuid", credential.getUuid());
        nameAndUuidMap.put("name", credential.getName());
        content.put("value", credential.getUuid());
        content.put("data", nameAndUuidMap);


        List<AttributeDefinition> attrs = AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("testCredentialAttribute", content));
        attrs.get(0).setType(AttributeType.CREDENTIAL);

        credentialService.loadFullCredentialData(attrs);

        Assertions.assertTrue(attrs.get(0).getContent() instanceof JsonAttributeContent);

        CredentialDto credentialDto = (CredentialDto) ((JsonAttributeContent) attrs.get(0).getContent()).getData();
        Assertions.assertEquals(credential.getUuid(), credentialDto.getUuid());
        Assertions.assertEquals(credential.getName(), credentialDto.getName());
    }

    @Test
    public void testLoadFullData_attributesNotFound() throws NotFoundException {
        HashMap<String, String> nameAndUuidMap = new HashMap<>();
        HashMap<String, Object> contentMap = new HashMap<>();
        nameAndUuidMap.put("uuid", "wrong-uuid");
        nameAndUuidMap.put("name", "wrong-name");
        contentMap.put("value","wrong-name");
        contentMap.put("data",nameAndUuidMap);


        List<AttributeDefinition> attrs = AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("testCredentialAttribute", contentMap));
        attrs.get(0).setType(AttributeType.CREDENTIAL);

        Assertions.assertThrows(NotFoundException.class, () -> credentialService.loadFullCredentialData(attrs));
    }

    @Test
    public void testLoadFullData_attributesEmpty() throws NotFoundException {
        credentialService.loadFullCredentialData(List.of()); // this should not throw exception

        List<AttributeDefinition> attrs = null;
        credentialService.loadFullCredentialData(attrs); // this should not throw exception
    }

    @Test
    public void testLoadFullData_attributesNonCredential() throws NotFoundException {
        credentialService.loadFullCredentialData(AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("dummyAttributes", null))); // this should not throw exception
    }

    @Test
    public void testLoadFullData_callback() throws NotFoundException {
        String credentialBodyKey = "testCredential";

        HashMap<String, String> nameAndUuidMap = new HashMap<>();
        nameAndUuidMap.put("uuid", credential.getUuid());
        nameAndUuidMap.put("name", credential.getName());

        HashMap<String, Serializable> attrib = new HashMap<>();
        attrib.put("value", credential.getName());
        attrib.put("data", nameAndUuidMap);

        AttributeCallbackMapping mapping = new AttributeCallbackMapping(
                "from",
                AttributeType.CREDENTIAL,
                credentialBodyKey,
                AttributeValueTarget.BODY);

        HashMap<String, Serializable> requestBodyMap = new HashMap<>();
        requestBodyMap.put(credentialBodyKey, attrib);

        AttributeCallback callback = new AttributeCallback();
        callback.setMappings(Set.of(mapping));

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setRequestBody(requestBodyMap);

        credentialService.loadFullCredentialData(callback, requestAttributeCallback);

        Assertions.assertTrue(requestAttributeCallback.getRequestBody().get(credentialBodyKey) instanceof CredentialDto);

        CredentialDto credentialDto = (CredentialDto) requestAttributeCallback.getRequestBody().get(credentialBodyKey);
        Assertions.assertEquals(credential.getUuid(), credentialDto.getUuid());
        Assertions.assertEquals(credential.getName(), credentialDto.getName());
    }

    @Test
    public void testLoadFullData_callbackValidation() {
        String credentialBodyKey = "testCredential";

        HashMap<String, String> nameAndUuidMap = new HashMap<>();
        nameAndUuidMap.put("uuid", "wrong-uuid");
        nameAndUuidMap.put("name", "wrong-name");

        HashMap<String, Serializable> attrib = new HashMap<>();
        attrib.put("value", "wrong-uuid");
        attrib.put("data", nameAndUuidMap);

        AttributeCallbackMapping mapping = new AttributeCallbackMapping(
                "from",
                AttributeType.CREDENTIAL,
                credentialBodyKey,
                AttributeValueTarget.BODY);

        HashMap<String, Serializable> requestBodyMap = new HashMap<>();

        AttributeCallback callback = new AttributeCallback();
        callback.setMappings(Set.of(mapping));

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setRequestBody(requestBodyMap);

        Assertions.assertThrows(ValidationException.class, () -> credentialService.loadFullCredentialData(callback, requestAttributeCallback));
    }

    @Test
    public void testLoadFullData_callbackValidationFailWrongValue() {
        String credentialBodyKey = "testCredential";

        AttributeCallbackMapping mapping = new AttributeCallbackMapping(
                "from",
                AttributeType.CREDENTIAL,
                credentialBodyKey,
                AttributeValueTarget.BODY);

        HashMap<String, Serializable> requestBodyMap = new HashMap<>();
        requestBodyMap.put(credentialBodyKey, "wrong-value");

        AttributeCallback callback = new AttributeCallback();
        callback.setMappings(Set.of(mapping));

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setRequestBody(requestBodyMap);

        Assertions.assertThrows(ValidationException.class, () -> credentialService.loadFullCredentialData(callback, requestAttributeCallback));
    }
}
