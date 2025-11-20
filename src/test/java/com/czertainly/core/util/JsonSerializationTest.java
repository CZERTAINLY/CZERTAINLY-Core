package com.czertainly.core.util;

import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttributeV2;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v2.content.CredentialAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.data.CredentialAttributeContentData;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public void testSerializeKeystore() throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("client1.p12");
        byte[] array = is.readAllBytes();
        System.out.println(Base64.getEncoder().encodeToString(array));
    }

    @Test
    public void testDeserializeRAProfileAttributes() {
        String attrData = "[{\"name\": \"tokenType\", \"content\": [{\"data\": \"PEM\"}]}, {\"name\": \"description\", \"content\": [{\"data\": \"DEMO RA Profile\"}]}, {\"name\": \"endEntityProfile\", \"content\": [{\"reference\": \"DemoTLSServerEndEntityProfile\", \"data\": {\"id\": 0, \"name\": \"DemoTLSServerEndEntityProfile\"}}]}, {\"name\": \"certificateProfile\", \"content\": [{\"reference\": \"DemoTLSServerEECertificateProfile\", \"data\": {\"id\": 0, \"name\": \"DemoTLSServerEECertificateProfile\"}}]}, {\"name\": \"certificationAuthority\", \"content\": [{\"reference\": \"DemoServerSubCA\", \"data\": {\"id\": 0, \"name\": \"DemoServerSubCA\"}}]}, {\"name\": \"sendNotifications\", \"content\": [{\"data\": false}]}, {\"name\": \"keyRecoverable\", \"content\": [{\"data\": true}]}]";

        List<BaseAttributeV2> attrs = AttributeDefinitionUtils.deserialize(attrData, BaseAttributeV2.class);
        Assertions.assertNotNull(attrs);
        Assertions.assertEquals(7, attrs.size());

        NameAndIdDto endEntityProfile = AttributeDefinitionUtils.getNameAndIdData("endEntityProfile", AttributeDefinitionUtils.getClientAttributes(attrs));
        Assertions.assertNotNull(endEntityProfile);
        Assertions.assertEquals(0, endEntityProfile.getId());
        Assertions.assertEquals("DemoTLSServerEndEntityProfile", endEntityProfile.getName());
    }

    @Test
    public void testSerializeCredential() {
        CredentialAttributeContentData credential = new CredentialAttributeContentData();
        credential.setName("test");

        List<DataAttributeV2> attrs = AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("credential", List.of(new CredentialAttributeContentV2("test", credential))));

        String serialized = AttributeDefinitionUtils.serialize(attrs);

        List<DataAttributeV2> deserialized = AttributeDefinitionUtils.deserialize(serialized, DataAttributeV2.class);

        CredentialAttributeContentData value = AttributeDefinitionUtils.getCredentialContent("credential", AttributeDefinitionUtils.getClientAttributes(deserialized));
        Assertions.assertNotNull(value);
    }

}
