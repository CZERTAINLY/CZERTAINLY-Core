package com.czertainly.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.czertainly.api.model.credential.CredentialDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.czertainly.api.core.modal.AuditLogFilter;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.NameAndIdDto;

public class JsonSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.findAndRegisterModules();
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Test
    public void testSerializeMap() throws JsonProcessingException {
        Map<Object, Object> data = new HashMap<>();
        data.put("testKey", LocalDateTime.now());
        data.put("nullKey", null);

        String json = MAPPER.writeValueAsString(data);
        System.out.println(json);
    }

    @Test
    public void testSerializeArray() throws JsonProcessingException {
        Object[][] data = new Object[][]{
                new Object[]{"testKey", "testValue"}
        };

        String json = MAPPER.writeValueAsString(data);
        System.out.println(json);
    }

    @Test
    public void testDeserializeTime() throws JsonProcessingException {
        String data = "{ \"createdFrom\": \"2021-02-15\"}";

        AuditLogFilter filter = MAPPER.readValue(data, AuditLogFilter.class);
        System.out.println(filter);
    }

    @Test
    public void testSerializeKeystore() throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("client1.p12");
        byte[] array = is.readAllBytes();
        System.out.println(Base64.getEncoder().encodeToString(array));
    }

    @Test
    public void testDeserializeRAProfileAttributes() {
        String attrData = "[{ \"name\": \"tokenType\", \"value\": \"PEM\" },{ \"name\": \"description\", \"value\": \"DEMO RA Profile\" },{ \"name\": \"endEntityProfile\", \"value\": { \"id\": 0, \"name\": \"DemoTLSServerEndEntityProfile\" } },{ \"name\": \"certificateProfile\", \"value\": { \"id\": 0, \"name\": \"DemoTLSServerEECertificateProfile\" } },{ \"name\": \"certificationAuthority\", \"value\": { \"id\": 0, \"name\": \"DemoServerSubCA\" } },{ \"name\": \"sendNotifications\", \"value\": false },{ \"name\": \"keyRecoverable\", \"value\": true }]";

        List<AttributeDefinition> attrs = AttributeDefinitionUtils.deserialize(attrData);
        Assertions.assertNotNull(attrs);
        Assertions.assertEquals(7, attrs.size());

        NameAndIdDto endEntityProfile = AttributeDefinitionUtils.getNameAndIdValue("endEntityProfile", attrs);
        Assertions.assertNotNull(endEntityProfile);
        Assertions.assertEquals(0, endEntityProfile.getId());
        Assertions.assertEquals("DemoTLSServerEndEntityProfile", endEntityProfile.getName());
    }

    @Test
    public void testSerializeCredential() {
        CredentialDto credential = new CredentialDto();
        credential.setName("test");

        List<AttributeDefinition> attrs = AttributeDefinitionUtils.createAttributes("credential", credential);

        String serialized = AttributeDefinitionUtils.serialize(attrs);

        List<AttributeDefinition> deserialized = AttributeDefinitionUtils.deserialize(serialized);

        Serializable value = AttributeDefinitionUtils.getAttributeValue("credential", deserialized);
        Assertions.assertNotNull(value);

        CredentialDto converted = MAPPER.convertValue(value, CredentialDto.class);

        Assertions.assertEquals(CredentialDto.class, converted.getClass());
    }

}
