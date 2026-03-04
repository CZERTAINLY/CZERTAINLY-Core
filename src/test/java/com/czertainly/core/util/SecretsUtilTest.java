package com.czertainly.core.util;

import com.czertainly.api.model.connector.secrets.content.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

class SecretsUtilTest {

    @Test
    void testFingerprintCalculation() throws NoSuchAlgorithmException, JsonProcessingException {

        String basicAuthContentWithExtraProperty = """
                {
                "password": "testPassword",
                "username": "testUsername",
                "extraProperty": "testExtraProperty",
                "type": "BASIC_AUTH"
                }
                """;
        String basicAuthContent = """
                {
                "username": "testUsername",
                "password": "testPassword",
                "type": "basicAuth"
                }
                """;
        ObjectMapper objectMapper = new ObjectMapper();
        BasicAuthSecretContent basicAuthSecretContent = objectMapper.readValue(basicAuthContent, BasicAuthSecretContent.class);
        JsonMapper jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        BasicAuthSecretContent basicAuthSecretContentWithExtraProperty = jsonMapper
                .readValue(basicAuthContentWithExtraProperty, BasicAuthSecretContent.class);
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(basicAuthSecretContent), SecretsUtil.calculateSecretContentFingerprint(basicAuthSecretContentWithExtraProperty));

        String keyValueContent1 = """
                {
                "type": "keyValue",
                "content": {
                "key1": "value1",
                "key2": "value2",
                "key3": null
                }
                }
                """;
        String keyValueContent2 = """
                {
                "type": "KEY_VALUE",
                "content": {
                "key2": "value2",
                "key1": "value1",
                "key3": null
                }
                }
                """;
        String keyValueContent3 = """
                {
                "type": "keyValue",
                "content": {
                "key2": "value2",
                "key1": "value1"
                }
                }
                """;
        KeyValueSecretContent keyValueSecretContent1 = objectMapper.readValue(keyValueContent1, KeyValueSecretContent.class);
        KeyValueSecretContent keyValueSecretContent2 = jsonMapper.readValue(keyValueContent2, KeyValueSecretContent.class);
        KeyValueSecretContent keyValueSecretContent3 = objectMapper.readValue(keyValueContent3, KeyValueSecretContent.class);
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(keyValueSecretContent1), SecretsUtil.calculateSecretContentFingerprint(keyValueSecretContent2));
        Assertions.assertNotEquals(SecretsUtil.calculateSecretContentFingerprint(keyValueSecretContent1), SecretsUtil.calculateSecretContentFingerprint(keyValueSecretContent3));

        KeyStoreSecretContent keyStoreSecretContent = new KeyStoreSecretContent(KeyStoreType.PKCS12, "content", "password");
        KeyStoreSecretContent keyStoreSecretContent2 = new KeyStoreSecretContent(KeyStoreType.PKCS12, "content", "password2");
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(keyStoreSecretContent), SecretsUtil.calculateSecretContentFingerprint(keyStoreSecretContent2));

        ApiKeySecretContent apiKeySecretContent = new ApiKeySecretContent("apiKeyValue");
        ApiKeySecretContent apiKeySecretContent2 = new ApiKeySecretContent("apiKeyValue");
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(apiKeySecretContent), SecretsUtil.calculateSecretContentFingerprint(apiKeySecretContent2));

        PrivateKeySecretContent privateKeySecretContent = new PrivateKeySecretContent("privateKeyValue");
        PrivateKeySecretContent privateKeySecretContent2 = new PrivateKeySecretContent("privateKeyValue");
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(privateKeySecretContent), SecretsUtil.calculateSecretContentFingerprint(privateKeySecretContent2));

        SecretKeySecretContent secretKeySecretContent = new SecretKeySecretContent("secretKeyValue");
        SecretKeySecretContent secretKeySecretContent2 = new SecretKeySecretContent("secretKeyValue");
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(secretKeySecretContent), SecretsUtil.calculateSecretContentFingerprint(secretKeySecretContent2));

        JwtTokenSecretContent jwtTokenSecretContent = new JwtTokenSecretContent("jwtTokenValue");
        JwtTokenSecretContent jwtTokenSecretContent2 = new JwtTokenSecretContent("jwtTokenValue");
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(jwtTokenSecretContent), SecretsUtil.calculateSecretContentFingerprint(jwtTokenSecretContent2));

        GenericSecretContent genericSecretContent = new GenericSecretContent("genericValue");
        GenericSecretContent genericSecretContent2 = new GenericSecretContent("genericValue");
        Assertions.assertEquals(SecretsUtil.calculateSecretContentFingerprint(genericSecretContent), SecretsUtil.calculateSecretContentFingerprint(genericSecretContent2));

    }
}
