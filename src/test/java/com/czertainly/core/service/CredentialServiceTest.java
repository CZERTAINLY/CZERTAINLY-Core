package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeValueTarget;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.CredentialAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.data.CredentialAttributeContentData;
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
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.*;

public class CredentialServiceTest extends BaseSpringBootTest {

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
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("credentialProviderConnector");
        connector.setUrl("http://localhost:"+mockServer.port());
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
        credential.setConnectorUuid(connector.getUuid());
        credential = credentialRepository.save(credential);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListCredentials() {
        List<CredentialDto> credentials = credentialService.listCredentials(SecurityFilter.create());
        Assertions.assertNotNull(credentials);
        Assertions.assertFalse(credentials.isEmpty());
        Assertions.assertEquals(1, credentials.size());
        Assertions.assertEquals(credential.getUuid().toString(), credentials.get(0).getUuid());
    }

    @Test
    public void testGetCredential() throws NotFoundException {
        CredentialDto dto = credentialService.getCredential(credential.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(credential.getUuid().toString(), dto.getUuid());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(credential.getConnectorUuid().toString(), dto.getConnectorUuid());
    }

    @Test
    public void testGetCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.getCredential(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
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
        request.setConnectorUuid(connector.getUuid().toString());
        request.setAttributes(List.of());
        request.setKind("ApiKey");

        CredentialDto dto = credentialService.createCredential(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(credential.getConnectorUuid().toString(), dto.getConnectorUuid());
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

        CredentialDto dto = credentialService.editCredential(credential.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(credential.getConnectorUuid().toString(), dto.getConnectorUuid());
    }

    @Test
    public void testEditCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.editCredential(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), null));
    }

    @Test
    public void testRemoveCredential() throws NotFoundException {
        credentialService.deleteCredential(credential.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.getCredential(credential.getSecuredUuid()));
    }

    @Test
    public void testRemoveCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.deleteCredential(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testEnableCredential() throws NotFoundException {
        credentialService.enableCredential(credential.getSecuredUuid());
        Assertions.assertEquals(true, credential.getEnabled());
    }

    @Test
    public void testEnableCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.enableCredential(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testDisableCredential() throws NotFoundException {
        credentialService.disableCredential(credential.getSecuredUuid());
        Assertions.assertEquals(false, credential.getEnabled());
    }

    @Test
    public void testDisableCredential_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.disableCredential(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        credentialService.bulkDeleteCredential(List.of(credential.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> credentialService.getCredential(credential.getSecuredUuid()));
    }

    @Test
    public void testLoadFullData_attributes() throws NotFoundException {
        CredentialAttributeContentData dto = new CredentialAttributeContentData();
        CredentialAttributeContent nameAndUuidMap = new CredentialAttributeContent();
        dto.setUuid(credential.getUuid().toString());
        dto.setName(credential.getName());
        nameAndUuidMap.setReference(credential.getUuid().toString());
        nameAndUuidMap.setData(dto);


        List<DataAttribute> attrs = AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("testCredentialAttribute", List.of(nameAndUuidMap)));
        attrs.get(0).setType(AttributeType.DATA);
        attrs.get(0).setContentType(AttributeContentType.CREDENTIAL);


        credentialService.loadFullCredentialData(attrs);

        Assertions.assertTrue(attrs.get(0).getContent().get(0).getData() instanceof CredentialAttributeContentData);

        CredentialAttributeContentData credentialDto = ((CredentialAttributeContent) attrs.get(0).getContent().get(0)).getData();
        Assertions.assertEquals(credential.getUuid().toString(), credentialDto.getUuid());
        Assertions.assertEquals(credential.getName(), credentialDto.getName());
    }

    @Test
    public void testLoadFullData_attributesNotFound() throws NotFoundException {
        CredentialAttributeContentData dto = new CredentialAttributeContentData();
        CredentialAttributeContent nameAndUuidMap = new CredentialAttributeContent();
        dto.setUuid("abfbc322-29e1-11ed-a261-0242ac120002");
        dto.setName("wrong-name");
        nameAndUuidMap.setReference("wrong-name");
        nameAndUuidMap.setData(dto);


        List<DataAttribute> attrs = AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("testCredentialAttribute", List.of(nameAndUuidMap)));
        attrs.get(0).setType(AttributeType.DATA);
        attrs.get(0).setContentType(AttributeContentType.CREDENTIAL);

        Assertions.assertThrows(NotFoundException.class, () -> credentialService.loadFullCredentialData(attrs));
    }


    @Test
    public void testLoadFullData_attributesNonCredential() throws NotFoundException {
        credentialService.loadFullCredentialData(AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("dummyAttributes", null))); // this should not throw exception
    }

    @Test
    public void testLoadFullData_callback() throws NotFoundException {
        String credentialBodyKey = "testCredential";

        CredentialAttributeContentData dto = new CredentialAttributeContentData();
        CredentialAttributeContent nameAndUuidMap = new CredentialAttributeContent();
        dto.setUuid(credential.getUuid().toString());
        dto.setName(credential.getName());
        nameAndUuidMap.setReference(credential.getUuid().toString());
        nameAndUuidMap.setData(dto);

        AttributeCallbackMapping mapping = new AttributeCallbackMapping(
                "from",
                AttributeType.DATA,
                AttributeContentType.CREDENTIAL,
                credentialBodyKey,
                Collections.singleton(AttributeValueTarget.BODY));
        ArrayList<CredentialAttributeContent> cont = new ArrayList<>();
        cont.add(nameAndUuidMap);
        HashMap<String, Serializable> requestBodyMap = new HashMap<>();
        requestBodyMap.put(credentialBodyKey, cont);

        AttributeCallback callback = new AttributeCallback();
        callback.setMappings(Set.of(mapping));

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setBody(requestBodyMap);

        credentialService.loadFullCredentialData(callback, requestAttributeCallback);

        Assertions.assertTrue(requestAttributeCallback.getBody().get(credentialBodyKey) instanceof CredentialAttributeContentData);

        CredentialAttributeContentData credentialDto = (CredentialAttributeContentData) requestAttributeCallback.getBody().get(credentialBodyKey);
        Assertions.assertEquals(credential.getUuid().toString(), credentialDto.getUuid());
        Assertions.assertEquals(credential.getName(), credentialDto.getName());
    }

    @Test
    public void testLoadFullData_callbackValidation() {
        String credentialBodyKey = "testCredential";

        CredentialAttributeContentData dto = new CredentialAttributeContentData();
        CredentialAttributeContent nameAndUuidMap = new CredentialAttributeContent();
        dto.setUuid("abfbc322-29e1-11ed-a261-0242ac120002");
        dto.setName("wrong-name");
        nameAndUuidMap.setReference("wrong-name");
        nameAndUuidMap.setData(dto);

        AttributeCallbackMapping mapping = new AttributeCallbackMapping(
                "from",
                AttributeType.DATA,
                AttributeContentType.CREDENTIAL,
                credentialBodyKey,
                Collections.singleton(AttributeValueTarget.BODY));

        HashMap<String, Serializable> requestBodyMap = new HashMap<>();

        AttributeCallback callback = new AttributeCallback();
        callback.setMappings(Set.of(mapping));

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setBody(requestBodyMap);

        Assertions.assertThrows(ValidationException.class, () -> credentialService.loadFullCredentialData(callback, requestAttributeCallback));
    }

    @Test
    public void testLoadFullData_callbackValidationFailWrongValue() {
        String credentialBodyKey = "testCredential";

        AttributeCallbackMapping mapping = new AttributeCallbackMapping(
                "from",
                AttributeType.DATA,
                AttributeContentType.CREDENTIAL,
                credentialBodyKey,
                Collections.singleton(AttributeValueTarget.BODY));

        HashMap<String, Serializable> requestBodyMap = new HashMap<>();
        requestBodyMap.put(credentialBodyKey, "wrong-value");

        AttributeCallback callback = new AttributeCallback();
        callback.setMappings(Set.of(mapping));

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setBody(requestBodyMap);

        Assertions.assertThrows(ValidationException.class, () -> credentialService.loadFullCredentialData(callback, requestAttributeCallback));
    }

    @Test
    public void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = credentialService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }
}
