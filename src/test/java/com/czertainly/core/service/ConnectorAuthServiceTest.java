package com.czertainly.core.service;

import com.czertainly.api.BaseApiClient;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.connector.AuthType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.czertainly.core.util.AttributeDefinitionUtils.createAttributes;
import static com.czertainly.api.BaseApiClient.*;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles = "SUPERADMINISTRATOR")
public class ConnectorAuthServiceTest {

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
        List<AttributeDefinition> attrs = new ArrayList<>();
        attrs.addAll(createAttributes(ATTRIBUTE_USERNAME, "username"));
        attrs.addAll(createAttributes(ATTRIBUTE_PASSWORD, "password"));

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

        List<AttributeDefinition> attrs = new ArrayList<>();
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE_TYPE, "PKCS12"));
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE, Base64.getEncoder().encodeToString(keyStoreData)));
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE_PASSWORD, "123456"));

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
        List<AttributeDefinition> attrs = new ArrayList<>();
        attrs.addAll(createAttributes(BaseApiClient.ATTRIBUTE_API_KEY, "apiKeySecret"));
        attrs.addAll(createAttributes(BaseApiClient.ATTRIBUTE_API_KEY_HEADER, "X-API-KEY"));

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
