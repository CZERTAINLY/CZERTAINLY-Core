package com.czertainly.core.service;

import com.czertainly.api.clients.BaseApiClient;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.content.FileAttributeContent;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_KEYSTORE;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_KEYSTORE_PASSWORD;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_KEYSTORE_TYPE;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_PASSWORD;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_USERNAME;
import static com.czertainly.core.util.AttributeDefinitionUtils.createAttributes;

@SpringBootTest
@Transactional
@Rollback
public class ConnectorAuthServiceTest extends BaseSpringBootTest {

    @Autowired
    private ConnectorAuthService connectorAuthService;

    @Test
    public void testGetAuthenticationTypes() {
        Set<AuthType> types = connectorAuthService.getAuthenticationTypes();
        Assertions.assertNotNull(types);
        Assertions.assertFalse(types.isEmpty());
    }


    @Test
    public void testGetBasicAuthAttributes() {
        List<AttributeDefinition> attrs = connectorAuthService.getBasicAuthAttributes();
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
    }

    @Test
    public void testValidateBasicAuthAttributes() {
        List<RequestAttributeDto> attrs = new ArrayList<>();


        attrs.addAll(createAttributes(ATTRIBUTE_USERNAME, new BaseAttributeContent<String>(){{setValue("username");}}));
        attrs.addAll(createAttributes(ATTRIBUTE_PASSWORD, new BaseAttributeContent<String>(){{setValue("password");}}));

        boolean result = connectorAuthService.validateBasicAuthAttributes(attrs);
        Assertions.assertTrue(result);
    }

    @Test
    public void testGetCertificateAttributes() {
        List<AttributeDefinition> attrs = connectorAuthService.getCertificateAttributes();
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
    }

    @Test
    public void testValidateCertificateAttributes() throws IOException {
        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        byte[] keyStoreData = keyStoreStream.readAllBytes();

        List<RequestAttributeDto> attrs = new ArrayList<>();

        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE_TYPE, new BaseAttributeContent<>(){{setValue("PKCS12");}}));
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE, new FileAttributeContent(){{setValue(Base64.getEncoder().encodeToString(keyStoreData));}}));
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE_PASSWORD, new BaseAttributeContent<>(){{setValue("123456");}}));

        boolean result = connectorAuthService.validateCertificateAttributes(attrs);
        Assertions.assertTrue(result);
    }

    @Test
    public void testGetApiKeyAuthAttributes() {
        List<AttributeDefinition> attrs = connectorAuthService.getApiKeyAuthAttributes();
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
    }

    @Test
    public void testValidateApiKeyAuthAttributes() {
        List<RequestAttributeDto> attrs = new ArrayList<>();
        BaseAttributeContent base = new BaseAttributeContent<String>();
        base.setValue("apiKetTesting");

        BaseAttributeContent header = new BaseAttributeContent<String>();
        header.setValue("X-API-KEY");

        attrs.addAll(createAttributes(BaseApiClient.ATTRIBUTE_API_KEY, base));
        attrs.addAll(createAttributes(BaseApiClient.ATTRIBUTE_API_KEY_HEADER, header));

        boolean result = connectorAuthService.validateApiKeyAuthAttributes(attrs);
        Assertions.assertTrue(result);
    }

    @Test
    public void testGetJWTAuthAttributes() {
        Assertions.assertThrows(ValidationException.class, () -> connectorAuthService.getJWTAuthAttributes());
    }

    @Test
    public void testValidateJWTAuthAttributes() {
        Assertions.assertThrows(ValidationException.class, () -> connectorAuthService.validateBasicAuthAttributes(List.of()));
    }
}
