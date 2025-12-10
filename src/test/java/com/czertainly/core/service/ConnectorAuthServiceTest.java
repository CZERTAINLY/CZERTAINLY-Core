package com.czertainly.core.service;

import com.czertainly.api.clients.BaseApiClient;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.*;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.content.data.FileAttributeContentData;
import com.czertainly.api.model.common.attribute.v3.DataAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.czertainly.api.model.common.attribute.v3.content.FileAttributeContentV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
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
import java.util.*;

import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_KEYSTORE;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_KEYSTORE_PASSWORD;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_KEYSTORE_TYPE;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_PASSWORD;
import static com.czertainly.api.clients.BaseApiClient.ATTRIBUTE_USERNAME;

@SpringBootTest
@Transactional
@Rollback
class ConnectorAuthServiceTest extends BaseSpringBootTest {

    @Autowired
    private ConnectorAuthService connectorAuthService;

    @Test
    void testGetAuthenticationTypes() {
        Set<AuthType> types = connectorAuthService.getAuthenticationTypes();
        Assertions.assertNotNull(types);
        Assertions.assertFalse(types.isEmpty());
    }


    @Test
    void testGetBasicAuthAttributes() {
        List<DataAttributeV3> attrs = connectorAuthService.getBasicAuthAttributes();
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
    }

    @Test
    void testValidateBasicAuthAttributes() {
        List<RequestAttribute> attrs = new ArrayList<>();


        attrs.addAll(createAttributes(ATTRIBUTE_USERNAME, List.of(new StringAttributeContentV3("username"))));
        attrs.addAll(createAttributes(ATTRIBUTE_PASSWORD, List.of(new StringAttributeContentV3("password"))));

        boolean result = connectorAuthService.validateBasicAuthAttributes(attrs);
        Assertions.assertTrue(result);
    }

    @Test
    void testGetCertificateAttributes() {
        List<DataAttributeV3> attrs = connectorAuthService.getCertificateAttributes();
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
    }

    @Test
    void testValidateCertificateAttributes() throws IOException {
        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        byte[] keyStoreData = keyStoreStream.readAllBytes();

        List<RequestAttribute> attrs = new ArrayList<>(createAttributes(ATTRIBUTE_KEYSTORE_TYPE, List.of(new StringAttributeContentV3("PKCS12"))));
        FileAttributeContentData data = new FileAttributeContentData();
        data.setContent(Base64.getEncoder().encodeToString(keyStoreData));
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE, List.of(new FileAttributeContentV3("", data))));
        attrs.addAll(createAttributes(ATTRIBUTE_KEYSTORE_PASSWORD, List.of(new StringAttributeContentV3("123456"))));

        boolean result = connectorAuthService.validateCertificateAttributes(attrs);
        Assertions.assertTrue(result);
    }

    @Test
    void testGetApiKeyAuthAttributes() {
        List<DataAttributeV3> attrs = connectorAuthService.getApiKeyAuthAttributes();
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
    }

    @Test
    void testValidateApiKeyAuthAttributes() {
        List<RequestAttribute> attrs = new ArrayList<>();
        BaseAttributeContentV3 base = new StringAttributeContentV3();
        base.setData("apiKetTesting");

        BaseAttributeContentV3 header = new StringAttributeContentV3();
        header.setData("X-API-KEY");

        attrs.addAll(createAttributes(BaseApiClient.ATTRIBUTE_API_KEY, List.of(base)));
        attrs.addAll(createAttributes(BaseApiClient.ATTRIBUTE_API_KEY_HEADER, List.of(header)));

        boolean result = connectorAuthService.validateApiKeyAuthAttributes(attrs);
        Assertions.assertTrue(result);
    }

    @Test
    void testGetJWTAuthAttributes() {
        Assertions.assertThrows(ValidationException.class, () -> connectorAuthService.getJWTAuthAttributes());
    }

    @Test
    void testValidateJWTAuthAttributes() {
        Assertions.assertThrows(ValidationException.class, () -> connectorAuthService.validateBasicAuthAttributes(List.of()));
    }

    private static List<RequestAttributeV3> createAttributes(String name, List<BaseAttributeContentV3<?>> content) {
        return createAttributes(UUID.randomUUID(), name, content);
    }

    private static List<RequestAttributeV3> createAttributes(UUID uuid, String name, List<BaseAttributeContentV3<?>> content) {
        RequestAttributeV3 attribute = new RequestAttributeV3(uuid, name, AttributeContentType.STRING, content);
        return List.of(attribute);
    }
}
